package com.example.flink.processor

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.SinkWriter
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import java.time.LocalDateTime
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OrderStateProcessor 단위 테스트.
 *
 * Flink MiniCluster 위에서 실행하여 실제 Flink 런타임 환경과 동일하게 테스트한다.
 * Kafka/ClickHouse 없이, 메모리 소스(fromData) → Processor → 메모리 Sink 구조로 검증.
 */
class OrderStateProcessorTest {

    companion object {
        /**
         * 로컬 MiniCluster 설정.
         * TaskManager 1개, Slot 2개로 구성된 미니 Flink 클러스터를 생성한다.
         * 실제 분산 환경은 아니지만 Flink 런타임(상태관리, 직렬화 등)은 동일하게 동작한다.
         */
        @JvmField
        val miniCluster = MiniClusterWithClientResource(
            MiniClusterResourceConfiguration.Builder()
                .setNumberSlotsPerTaskManager(2)
                .setNumberTaskManagers(1)
                .build()
        )
    }

    /**
     * 테스트용 Sink (Sink2 API).
     *
     * Flink 1.20에서 기존 SinkFunction은 deprecated 되었고,
     * 새로운 Sink2 API(Sink + SinkWriter)를 사용해야 한다.
     *
     * 구조:
     *   Sink (팩토리) → createWriter() → SinkWriter (실제 데이터 수신)
     *
     * SinkWriter.write()가 호출될 때마다 static 리스트에 결과를 수집하여
     * 테스트에서 검증할 수 있게 한다.
     *
     * static 리스트를 쓰는 이유:
     *   Flink가 Sink 객체를 직렬화하여 TaskManager로 전송하므로,
     *   인스턴스 필드는 직렬화/역직렬화 과정에서 초기화된다.
     *   static(companion object)은 같은 JVM 내에서 공유되므로 테스트에서 접근 가능.
     */
    class CollectSink : Sink<OrderState> {
        @Suppress("DEPRECATION")
        override fun createWriter(context: Sink.InitContext): SinkWriter<OrderState> {
            return CollectWriter()
        }

        class CollectWriter : SinkWriter<OrderState> {
            override fun write(element: OrderState, context: SinkWriter.Context) {
                synchronized(values) { values.add(element) }
            }
            override fun flush(endOfInput: Boolean) {}
            override fun close() {}
        }

        companion object {
            /** 테스트 결과 수집용 thread-safe 리스트. 각 테스트 시작 시 clear() 필수. */
            val values: MutableList<OrderState> = Collections.synchronizedList(mutableListOf())
        }
    }

    /**
     * 테스트용 NormalizedOrderEvent 생성 헬퍼.
     * 매번 모든 필드를 채우는 번거로움을 줄이기 위해 기본값을 제공한다.
     */
    private fun event(
        orderId: String,
        type: OrderEventType,
        qty: Int = 100,
        price: Long = 50000L,
        symbol: String = "BTC/USD"
    ) = NormalizedOrderEvent(
        eventTime = LocalDateTime.now(),
        eventId = "evt-${System.nanoTime()}",
        orderId = orderId,
        accountId = "ACC_00001",
        symbol = symbol,
        eventType = type,
        quantity = qty,
        price = price,
        rawPayload = "{}"
    )

    /**
     * 주문 생명주기 전체 테스트: 생성 → 부분체결 → 완전체결.
     *
     * 검증 포인트:
     * - CREATED: originalQty=100, remainingQty=100
     * - PARTIALLY_FILLED: filledQty=40, remainingQty=60
     * - FILLED: filledQty=100, remainingQty=0
     */
    @Test
    fun `주문 생성 후 부분체결 후 완전체결`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val events = listOf(
                event("ORD-001", OrderEventType.CREATED, qty = 100),
                event("ORD-001", OrderEventType.PARTIALLY_FILLED, qty = 40),
                event("ORD-001", OrderEventType.FILLED, qty = 100)
            )

            // fromData(): fromCollection()의 non-deprecated 대체 API (Flink 1.20+)
            // 테스트 데이터를 메모리에서 바로 스트림으로 생성한다.
            env.fromData(events)
                .keyBy { it.orderId }
                .process(OrderStateProcessor())
                // sinkTo(): addSink()의 non-deprecated 대체 API
                // Sink2 인터페이스를 구현한 CollectSink를 연결한다.
                .sinkTo(CollectSink())

            env.execute("test-order-lifecycle")

            val results = CollectSink.values
            assertEquals(3, results.size)

            // CREATED: 주문 생성 직후 — 아직 체결 없음
            assertEquals(OrderStatus.CREATED, results[0].status)
            assertEquals(100, results[0].originalQty)
            assertEquals(100, results[0].remainingQty)

            // PARTIALLY_FILLED: 40주 체결 → 60주 남음
            assertEquals(OrderStatus.PARTIALLY_FILLED, results[1].status)
            assertEquals(40, results[1].filledQty)
            assertEquals(60, results[1].remainingQty)

            // FILLED: 전량 체결 완료 → 남은 수량 0
            assertEquals(OrderStatus.FILLED, results[2].status)
            assertEquals(100, results[2].filledQty)
            assertEquals(0, results[2].remainingQty)

        } finally {
            miniCluster.after()
        }
    }

    /**
     * 주문 수량 변경(MODIFIED) 테스트.
     *
     * 시나리오: 100주 → 150주로 수량 변경
     * 검증: currentQty=150, remainingQty=150 (아직 체결 없으므로 전량 대기)
     */
    @Test
    fun `주문 수정 시 수량 업데이트`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val events = listOf(
                event("ORD-002", OrderEventType.CREATED, qty = 100),
                event("ORD-002", OrderEventType.MODIFIED, qty = 150)
            )

            env.fromData(events)
                .keyBy { it.orderId }
                .process(OrderStateProcessor())
                .sinkTo(CollectSink())

            env.execute("test-order-modify")

            val results = CollectSink.values
            assertEquals(2, results.size)
            assertEquals(150, results[1].currentQty)
            assertEquals(150, results[1].remainingQty)

        } finally {
            miniCluster.after()
        }
    }

    /**
     * 주문 취소(CANCELED) 테스트.
     *
     * 시나리오: 주문 생성 후 취소
     * 검증: status=CANCELED
     */
    @Test
    fun `주문 취소`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val events = listOf(
                event("ORD-003", OrderEventType.CREATED, qty = 100),
                event("ORD-003", OrderEventType.CANCELED, qty = 100)
            )

            env.fromData(events)
                .keyBy { it.orderId }
                .process(OrderStateProcessor())
                .sinkTo(CollectSink())

            env.execute("test-order-cancel")

            val results = CollectSink.values
            assertEquals(2, results.size)
            assertEquals(OrderStatus.CANCELED, results[1].status)

        } finally {
            miniCluster.after()
        }
    }
}
