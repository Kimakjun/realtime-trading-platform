package com.example.flink.processor

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness
import org.apache.flink.streaming.api.operators.KeyedProcessOperator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MatchingEngineProcessorTest {

    private lateinit var testHarness: KeyedOneInputStreamOperatorTestHarness<String, NormalizedOrderEvent, String>
    private lateinit var processOperator: KeyedProcessOperator<String, NormalizedOrderEvent, String>

    @BeforeEach
    fun setup() {
        val processor = MatchingEngineProcessor()
        processOperator = KeyedProcessOperator(processor)
        testHarness = KeyedOneInputStreamOperatorTestHarness(
            processOperator,
            { it.symbol }, // KeySelector
            TypeInformation.of(String::class.java)
        )
        testHarness.open()
    }

    @Test
    fun `should correctly match duplicate limit orders without index out of bounds or consistency errors`() {
        // Given 2 identical SELL orders for AAPL
        val sell1 = NormalizedOrderEvent(
            eventId = "e1",
            eventTime = LocalDateTime.now(),
            orderId = "order-1",
            accountId = "acc-1",
            symbol = "AAPL",
            eventType = OrderEventType.CREATED,
            side = "SELL",
            quantity = 5,
            price = 15000L,
            rawPayload = "{}"
        )
        
        val sell2 = NormalizedOrderEvent(
            eventId = "e2",
            eventTime = LocalDateTime.now().plusNanos(100000000), // +100ms
            orderId = "order-2",
            accountId = "acc-2",
            symbol = "AAPL",
            eventType = OrderEventType.CREATED,
            side = "SELL",
            quantity = 5,
            price = 15000L,
            rawPayload = "{}"
        )

        testHarness.processElement(sell1, 1L)
        testHarness.processElement(sell2, 2L)
        
        // Output should be empty since no match yet
        assertTrue(testHarness.extractOutputValues().isEmpty())

        // When a BUY order comes in that consumes 7 units (1 full from sell1, 2 from sell2)
        val buy = NormalizedOrderEvent(
            eventId = "e3",
            eventTime = LocalDateTime.now().plusNanos(200000000), // +200ms
            orderId = "order-buy",
            accountId = "acc-3",
            symbol = "AAPL",
            eventType = OrderEventType.CREATED,
            side = "BUY",
            quantity = 7,
            price = 15000L,
            rawPayload = "{}"
        )
        
        testHarness.processElement(buy, 3L)

        // Then executions should be produced
        val output = testHarness.extractOutputValues()
        
        // 4 execution events total: 
        // 1 BUY + 1 SELL (for the first 5 units against sell1)
        // 1 BUY + 1 SELL (for the next 2 units against sell2)
        assertEquals(4, output.size)

        // Validate that sell1 was fully filled (matchQty = 5) and sell2 was partially filled (matchQty = 2)
        val sell1Execs = output.filter { it.contains("order-1") }
        val sell2Execs = output.filter { it.contains("order-2") }
        
        assertEquals(1, sell1Execs.size, "sell1 should have 1 execution")
        assertTrue(sell1Execs[0].contains("\"quantity\":5"))
        
        assertEquals(1, sell2Execs.size, "sell2 should have 1 partial execution")
        assertTrue(sell2Execs[0].contains("\"quantity\":2"))
    }
}
