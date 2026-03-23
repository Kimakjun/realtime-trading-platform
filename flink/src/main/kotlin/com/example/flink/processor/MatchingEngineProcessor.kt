package com.example.flink.processor

import com.example.flink.model.ExecutionEvent
import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.flink.api.common.state.ListState
import org.apache.flink.api.common.state.ListStateDescriptor
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import org.apache.flink.util.OutputTag

class MatchingEngineProcessor : KeyedProcessFunction<String, NormalizedOrderEvent, String>() {

    companion object {
        @JvmStatic
        val EXECUTION_STATE_OUTPUT_TAG = object : OutputTag<NormalizedOrderEvent>("execution-states") {}
    }

    // States for OrderBook
    @Transient private lateinit var buyOrdersState: ListState<NormalizedOrderEvent>
    @Transient private lateinit var sellOrdersState: ListState<NormalizedOrderEvent>
    @Transient private lateinit var mapper: com.fasterxml.jackson.databind.ObjectMapper

    override fun open(parameters: Configuration) {
        val buyDescriptor = ListStateDescriptor("buy-orders", NormalizedOrderEvent::class.java)
        buyOrdersState = runtimeContext.getListState(buyDescriptor)

        val sellDescriptor = ListStateDescriptor("sell-orders", NormalizedOrderEvent::class.java)
        sellOrdersState = runtimeContext.getListState(sellDescriptor)
        
        mapper = jacksonObjectMapper()
    }

    override fun processElement(
        event: NormalizedOrderEvent,
        ctx: Context,
        out: Collector<String>
    ) {
        // Only process NEW orders for simplicity in this demo matching engine
        if (event.eventType != OrderEventType.CREATED && event.eventType != OrderEventType.from("NEW")) return

        var remainingQty = event.quantity
        val formatter = DateTimeFormatter.ISO_INSTANT

        if (event.side == "BUY") {
            val sells = sellOrdersState.get()?.toMutableList() ?: mutableListOf()
            // Sort by price ASC, then time ASC
            sells.sortWith(compareBy({ it.price }, { it.eventTime }))

            val iterator = sells.listIterator()
            while (iterator.hasNext() && remainingQty > 0) {
                val sell = iterator.next()
                if (sell.price <= event.price) {
                    val matchQty = minOf(remainingQty, sell.quantity)
                    val matchPrice = sell.price // Maker's price
                    val executionId = UUID.randomUUID().toString()
                    val timestamp = java.time.Instant.now().let { formatter.format(it) }

                    // Output BUY execution
                    val buyExec = ExecutionEvent(
                        executionId, event.orderId, event.accountId, event.symbol, "BUY", matchQty, matchPrice, timestamp
                    )
                    out.collect(mapper.writeValueAsString(buyExec))

                    // Output SELL execution
                    val sellExec = ExecutionEvent(
                        executionId, sell.orderId, sell.accountId, sell.symbol, "SELL", matchQty, matchPrice, timestamp
                    )
                    out.collect(mapper.writeValueAsString(sellExec))

                    val buyStatus = if (remainingQty - matchQty <= 0) OrderEventType.FILLED else OrderEventType.PARTIALLY_FILLED
                    val sellStatus = if (sell.quantity - matchQty <= 0) OrderEventType.FILLED else OrderEventType.PARTIALLY_FILLED

                    ctx.output(EXECUTION_STATE_OUTPUT_TAG, NormalizedOrderEvent(
                        eventId = executionId + "-buy",
                        orderId = event.orderId,
                        accountId = event.accountId,
                        symbol = event.symbol,
                        side = "BUY",
                        eventType = buyStatus,
                        quantity = matchQty,
                        price = matchPrice,
                        eventTime = java.time.LocalDateTime.now(),
                        rawPayload = "{}"
                    ))

                    ctx.output(EXECUTION_STATE_OUTPUT_TAG, NormalizedOrderEvent(
                        eventId = executionId + "-sell",
                        orderId = sell.orderId,
                        accountId = sell.accountId,
                        symbol = sell.symbol,
                        side = "SELL",
                        eventType = sellStatus,
                        quantity = matchQty,
                        price = matchPrice,
                        eventTime = java.time.LocalDateTime.now(),
                        rawPayload = "{}"
                    ))

                    remainingQty -= matchQty
                    
                    // Update sell order in queue
                    val newSellQty = sell.quantity - matchQty
                    if (newSellQty <= 0) {
                        iterator.remove()
                    } else {
                        // Safe modification without indexOf
                        iterator.set(sell.copy(quantity = newSellQty))
                    }
                } else {
                    break // Remaining sells are too expensive
                }
            }
            sellOrdersState.update(sells)

            if (remainingQty > 0) {
                val buys = buyOrdersState.get()?.toMutableList() ?: mutableListOf()
                buys.add(event.copy(quantity = remainingQty))
                buyOrdersState.update(buys)
            }

        } else if (event.side == "SELL") {
            val buys = buyOrdersState.get()?.toMutableList() ?: mutableListOf()
            // Sort by price DESC, then time ASC
            buys.sortWith(compareByDescending<NormalizedOrderEvent> { it.price }.thenBy { it.eventTime })

            val iterator = buys.listIterator()
            while (iterator.hasNext() && remainingQty > 0) {
                val buy = iterator.next()
                if (buy.price >= event.price) {
                    val matchQty = minOf(remainingQty, buy.quantity)
                    val matchPrice = buy.price // Maker's price
                    val executionId = UUID.randomUUID().toString()
                    val timestamp = java.time.Instant.now().let { formatter.format(it) }

                    // Output SELL execution
                    val sellExec = ExecutionEvent(
                        executionId, event.orderId, event.accountId, event.symbol, "SELL", matchQty, matchPrice, timestamp
                    )
                    out.collect(mapper.writeValueAsString(sellExec))

                    // Output BUY execution
                    val buyExec = ExecutionEvent(
                        executionId, buy.orderId, buy.accountId, buy.symbol, "BUY", matchQty, matchPrice, timestamp
                    )
                    out.collect(mapper.writeValueAsString(buyExec))

                    val sellStatus = if (remainingQty - matchQty <= 0) OrderEventType.FILLED else OrderEventType.PARTIALLY_FILLED
                    val buyStatus = if (buy.quantity - matchQty <= 0) OrderEventType.FILLED else OrderEventType.PARTIALLY_FILLED

                    ctx.output(EXECUTION_STATE_OUTPUT_TAG, NormalizedOrderEvent(
                        eventId = executionId + "-sell",
                        orderId = event.orderId,
                        accountId = event.accountId,
                        symbol = event.symbol,
                        side = "SELL",
                        eventType = sellStatus,
                        quantity = matchQty,
                        price = matchPrice,
                        eventTime = java.time.LocalDateTime.now(),
                        rawPayload = "{}"
                    ))

                    ctx.output(EXECUTION_STATE_OUTPUT_TAG, NormalizedOrderEvent(
                        eventId = executionId + "-buy",
                        orderId = buy.orderId,
                        accountId = buy.accountId,
                        symbol = buy.symbol,
                        side = "BUY",
                        eventType = buyStatus,
                        quantity = matchQty,
                        price = matchPrice,
                        eventTime = java.time.LocalDateTime.now(),
                        rawPayload = "{}"
                    ))

                    remainingQty -= matchQty
                    
                    // Update buy order in queue
                    val newBuyQty = buy.quantity - matchQty
                    if (newBuyQty <= 0) {
                        iterator.remove()
                    } else {
                        // Safe modification without indexOf
                        iterator.set(buy.copy(quantity = newBuyQty))
                    }
                } else {
                    break // Remaining buys are too cheap
                }
            }
            buyOrdersState.update(buys)

            if (remainingQty > 0) {
                val sells = sellOrdersState.get()?.toMutableList() ?: mutableListOf()
                sells.add(event.copy(quantity = remainingQty))
                sellOrdersState.update(sells)
            }
        }
    }
}
