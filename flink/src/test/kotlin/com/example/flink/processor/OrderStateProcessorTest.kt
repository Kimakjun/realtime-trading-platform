package com.example.flink.processor

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.test.util.MiniClusterWithClientResource
import java.time.LocalDateTime
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderStateProcessorTest {

    companion object {
        @JvmField
        val miniCluster = MiniClusterWithClientResource(
            MiniClusterResourceConfiguration.Builder()
                .setNumberSlotsPerTaskManager(2)
                .setNumberTaskManagers(1)
                .build()
        )
    }

    class CollectSink : SinkFunction<OrderState> {
        override fun invoke(value: OrderState, context: SinkFunction.Context) {
            synchronized(values) { values.add(value) }
        }
        companion object {
            val values: MutableList<OrderState> = Collections.synchronizedList(mutableListOf())
        }
    }

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

            env.fromCollection(events)
                .keyBy(KeySelector<NormalizedOrderEvent, String> { it.orderId })
                .process(OrderStateProcessor())
                .addSink(CollectSink())

            env.execute("test-order-lifecycle")

            val results = CollectSink.values
            assertEquals(3, results.size)

            // CREATED
            assertEquals(OrderStatus.CREATED, results[0].status)
            assertEquals(100, results[0].originalQty)
            assertEquals(100, results[0].remainingQty)

            // PARTIALLY_FILLED
            assertEquals(OrderStatus.PARTIALLY_FILLED, results[1].status)
            assertEquals(40, results[1].filledQty)
            assertEquals(60, results[1].remainingQty)

            // FILLED
            assertEquals(OrderStatus.FILLED, results[2].status)
            assertEquals(100, results[2].filledQty)
            assertEquals(0, results[2].remainingQty)

        } finally {
            miniCluster.after()
        }
    }

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

            env.fromCollection(events)
                .keyBy(KeySelector<NormalizedOrderEvent, String> { it.orderId })
                .process(OrderStateProcessor())
                .addSink(CollectSink())

            env.execute("test-order-modify")

            val results = CollectSink.values
            assertEquals(2, results.size)
            assertEquals(150, results[1].currentQty)
            assertEquals(150, results[1].remainingQty)

        } finally {
            miniCluster.after()
        }
    }

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

            env.fromCollection(events)
                .keyBy(KeySelector<NormalizedOrderEvent, String> { it.orderId })
                .process(OrderStateProcessor())
                .addSink(CollectSink())

            env.execute("test-order-cancel")

            val results = CollectSink.values
            assertEquals(2, results.size)
            assertEquals(OrderStatus.CANCELED, results[1].status)

        } finally {
            miniCluster.after()
        }
    }
}
