package com.example.flink.pipeline

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import com.example.flink.parser.OrderEventParser
import com.example.flink.processor.OrderStateProcessor
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.SinkWriter
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 전체 파이프라인 통합 테스트.
 *
 * 실제 운영 파이프라인과 동일한 구조를 MiniCluster 위에서 실행한다:
 *   JSON 문자열 → OrderEventParser → keyBy(orderId) → OrderStateProcessor → Sink
 *
 * Kafka/ClickHouse를 fromData/CollectSink로 대체하여
 * 외부 인프라 없이 파이프라인 전체 동작을 검증한다.
 */
class OrderProcessingPipelineTest {

    companion object {
        /** 로컬 MiniCluster — TaskManager 1개, Slot 2개 */
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
     * 제네릭 타입을 사용하여 OrderState 외의 타입에도 재사용 가능.
     *
     * SinkWriter.write()가 호출될 때마다 static 리스트에 결과를 수집한다.
     * 제네릭 T를 Any로 캐스팅하여 companion object의 values에 저장하는 구조.
     */
    class CollectSink<T> : Sink<T> {
        @Suppress("DEPRECATION")
        override fun createWriter(context: Sink.InitContext): SinkWriter<T> {
            return object : SinkWriter<T> {
                override fun write(element: T, context: SinkWriter.Context) {
                    synchronized(values) {
                        @Suppress("UNCHECKED_CAST")
                        (values as MutableList<T>).add(element)
                    }
                }
                override fun flush(endOfInput: Boolean) {}
                override fun close() {}
            }
        }

        companion object {
            val values: MutableList<Any> = Collections.synchronizedList(mutableListOf())
        }
    }

    /**
     * 전체 파이프라인 E2E 테스트 — JSON 입력에서 OrderState 출력까지.
     *
     * 테스트 데이터:
     * - ORD-001: CREATED → PARTIALLY_FILLED(30주) → FILLED (3개 이벤트, 3개 상태 출력)
     * - ORD-002: CREATED (1개 이벤트, 1개 상태 출력)
     * - orderId 없는 이벤트: 드랍
     * - 잘못된 JSON: 드랍
     *
     * 기대 결과: 유효 4개 이벤트 → OrderState 4개 출력
     */
    @Test
    fun `MiniCluster 전체 파이프라인 - JSON에서 OrderState까지`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val testEvents = listOf(
                """{"event_id":"e1","order_id":"ORD-001","account_id":"ACC_00001","symbol":"BTC/USD","event_type":"CREATED","side":"BUY","quantity":100,"price":50000,"event_time":"2026-03-22 10:00:00"}""",
                """{"event_id":"e2","order_id":"ORD-001","account_id":"ACC_00001","symbol":"BTC/USD","event_type":"PARTIALLY_FILLED","side":"BUY","quantity":30,"price":50000,"event_time":"2026-03-22 10:01:00"}""",
                """{"event_id":"e3","order_id":"ORD-002","account_id":"ACC_00002","symbol":"ETH/USD","event_type":"CREATED","side":"SELL","quantity":200,"price":3000,"event_time":"2026-03-22 10:02:00"}""",
                """{"event_id":"e4","order_id":"ORD-001","account_id":"ACC_00001","symbol":"BTC/USD","event_type":"FILLED","side":"BUY","quantity":100,"price":50000,"event_time":"2026-03-22 10:03:00"}""",
                // 아래 이벤트들은 Parser에서 드랍되어야 한다
                """{"event_id":"e5","symbol":"BTC/USD","event_type":"CREATED","side":"BUY","quantity":100,"price":50000}""",
                """not a json at all"""
            )

            // fromData(): Kafka 대신 메모리에서 테스트 데이터를 스트림으로 생성
            val rawStream = env.fromData(testEvents)

            // 실제 운영 파이프라인과 동일한 operator 체인 (Kafka Source/ClickHouse Sink만 대체)
            val normalizedStream = rawStream.flatMap(OrderEventParser())

            val stateStream = normalizedStream
                .keyBy { it.orderId }
                .process(OrderStateProcessor())

            stateStream.sinkTo(CollectSink())

            env.execute("test-pipeline")

            @Suppress("UNCHECKED_CAST")
            val results = CollectSink.values as List<OrderState>

            // 유효 이벤트 4개 → OrderState 4개 출력
            assertEquals(4, results.size)

            // ORD-001: 3개 이벤트 → 최종 FILLED
            val ord001States = results.filter { it.orderId == "ORD-001" }
            assertEquals(3, ord001States.size)
            assertEquals(OrderStatus.FILLED, ord001States.last().status)
            assertEquals(0, ord001States.last().remainingQty)

            // ORD-002: 1개 이벤트 → CREATED 상태 유지
            val ord002States = results.filter { it.orderId == "ORD-002" }
            assertEquals(1, ord002States.size)
            assertEquals(OrderStatus.CREATED, ord002States[0].status)
            assertEquals(200, ord002States[0].originalQty)

        } finally {
            miniCluster.after()
        }
    }

    /**
     * 잘못된 이벤트 필터링 테스트.
     *
     * 모든 입력이 유효하지 않으므로 OrderEventParser에서 전부 드랍되어야 한다.
     * - qty=0: 수량 0 이하 → 드랍
     * - price=-1: 가격 음수 → 드랍
     * - orderId 없음 → 드랍
     * - 잘못된 JSON → 드랍
     * - 빈 문자열 → 드랍
     */
    @Test
    fun `잘못된 이벤트는 파이프라인에서 필터링`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val invalidEvents = listOf(
                """{"order_id":"ORD-001","event_type":"CREATED","side":"BUY","quantity":0,"price":50000}""",
                """{"order_id":"ORD-002","event_type":"CREATED","side":"SELL","quantity":100,"price":-1}""",
                """{"event_type":"CREATED","side":"BUY","quantity":100,"price":50000}""",
                """invalid json""",
                ""
            )

            val rawStream = env.fromData(invalidEvents)
            val normalizedStream = rawStream.flatMap(OrderEventParser())

            val stateStream = normalizedStream
                .keyBy { it.orderId }
                .process(OrderStateProcessor())

            stateStream.sinkTo(CollectSink())
            env.execute("test-invalid-pipeline")

            assertTrue(CollectSink.values.isEmpty(), "잘못된 이벤트는 모두 드랍되어야 함")

        } finally {
            miniCluster.after()
        }
    }
}
