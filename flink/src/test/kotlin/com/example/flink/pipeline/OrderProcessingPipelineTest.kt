package com.example.flink.pipeline

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import com.example.flink.parser.OrderEventParser
import com.example.flink.processor.OrderStateProcessor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.test.util.MiniClusterWithClientResource
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /** 테스트용 Sink — 결과를 static 리스트에 수집 */
    class CollectSink<T> : SinkFunction<T> {
        override fun invoke(value: T, context: SinkFunction.Context) {
            synchronized(values) {
                @Suppress("UNCHECKED_CAST")
                (values as MutableList<T>).add(value)
            }
        }

        companion object {
            val values: MutableList<Any> = Collections.synchronizedList(mutableListOf())
        }
    }

    @Test
    fun `MiniCluster 전체 파이프라인 - JSON에서 OrderState까지`() {
        // MiniCluster 시작
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            // 테스트 입력 데이터 — Kafka 대신 fromCollection 사용
            val testEvents = listOf(
                """{"eventId":"e1","orderId":"ORD-001","accountId":"ACC_00001","symbol":"BTC/USD","type":"CREATED","qty":100,"price":50000,"eventTime":"2026-03-22 10:00:00"}""",
                """{"eventId":"e2","orderId":"ORD-001","accountId":"ACC_00001","symbol":"BTC/USD","type":"PARTIALLY_FILLED","qty":30,"price":50000,"eventTime":"2026-03-22 10:01:00"}""",
                """{"eventId":"e3","orderId":"ORD-002","accountId":"ACC_00002","symbol":"ETH/USD","type":"CREATED","qty":200,"price":3000,"eventTime":"2026-03-22 10:02:00"}""",
                """{"eventId":"e4","orderId":"ORD-001","accountId":"ACC_00001","symbol":"BTC/USD","type":"FILLED","qty":100,"price":50000,"eventTime":"2026-03-22 10:03:00"}""",
                // 드랍되어야 할 이벤트들
                """{"eventId":"e5","symbol":"BTC/USD","type":"CREATED","qty":100,"price":50000}""",
                """not a json at all"""
            )

            val rawStream = env.fromCollection(testEvents)

            // 실제 파이프라인과 동일한 구조 (Kafka/ClickHouse만 제외)
            val normalizedStream = rawStream.flatMap(OrderEventParser())

            val stateStream = normalizedStream
                .keyBy(KeySelector<NormalizedOrderEvent, String> { it.orderId })
                .process(OrderStateProcessor())

            stateStream.addSink(CollectSink())

            env.execute("test-pipeline")

            // 검증
            @Suppress("UNCHECKED_CAST")
            val results = CollectSink.values as List<OrderState>

            // 유효 이벤트 4개 → OrderState 4개 출력
            assertEquals(4, results.size)

            // ORD-001 최종 상태 확인
            val ord001States = results.filter { it.orderId == "ORD-001" }
            assertEquals(3, ord001States.size)
            assertEquals(OrderStatus.FILLED, ord001States.last().status)
            assertEquals(0, ord001States.last().remainingQty)

            // ORD-002 상태 확인
            val ord002States = results.filter { it.orderId == "ORD-002" }
            assertEquals(1, ord002States.size)
            assertEquals(OrderStatus.CREATED, ord002States[0].status)
            assertEquals(200, ord002States[0].originalQty)

        } finally {
            miniCluster.after()
        }
    }

    @Test
    fun `잘못된 이벤트는 파이프라인에서 필터링`() {
        miniCluster.before()
        CollectSink.values.clear()

        try {
            val env = StreamExecutionEnvironment.getExecutionEnvironment()
            env.parallelism = 1

            val invalidEvents = listOf(
                """{"orderId":"ORD-001","type":"CREATED","qty":0,"price":50000}""",
                """{"orderId":"ORD-002","type":"CREATED","qty":100,"price":-1}""",
                """{"type":"CREATED","qty":100,"price":50000}""",
                """invalid json""",
                ""
            )

            val rawStream = env.fromCollection(invalidEvents)
            val normalizedStream = rawStream.flatMap(OrderEventParser())

            val stateStream = normalizedStream
                .keyBy(KeySelector<NormalizedOrderEvent, String> { it.orderId })
                .process(OrderStateProcessor())

            stateStream.addSink(CollectSink())
            env.execute("test-invalid-pipeline")

            assertTrue(CollectSink.values.isEmpty(), "잘못된 이벤트는 모두 드랍되어야 함")

        } finally {
            miniCluster.after()
        }
    }
}
