package com.example.flink.processor

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

class OrderStateProcessor : KeyedProcessFunction<String, NormalizedOrderEvent, OrderState>() {
    private val log = LoggerFactory.getLogger(OrderStateProcessor::class.java)

    @Transient private lateinit var orderState: ValueState<OrderState>

    override fun open(parameters: Configuration) {
        val descriptor =
                ValueStateDescriptor("orderState", TypeInformation.of(OrderState::class.java))
        orderState = runtimeContext.getState(descriptor)
    }

    override fun processElement(
            event: NormalizedOrderEvent,
            ctx: Context,
            out: Collector<OrderState>
    ) {
        val currentState = orderState.value()

        val newState =
                if (currentState == null) {
                    // First time seeing this orderId
                    when (event.eventType) {
                        OrderEventType.CREATED -> {
                            OrderState(
                                    orderId = event.orderId,
                                    symbol = event.symbol,
                                    status = OrderStatus.CREATED,
                                    originalQty = event.quantity,
                                    currentQty = event.quantity,
                                    filledQty = 0,
                                    remainingQty = event.quantity,
                                    lastEventTime = event.eventTime
                            )
                        }
                        else -> {
                            log.warn(
                                    "Received non-CREATED event for unknown order {}: {}",
                                    event.orderId,
                                    event.eventType
                            )
                            // We can either drop it or initialize a degraded state. Let's
                            // initialize based on event
                            val status =
                                    when (event.eventType) {
                                        OrderEventType.PARTIALLY_FILLED ->
                                                OrderStatus.PARTIALLY_FILLED
                                        OrderEventType.FILLED -> OrderStatus.FILLED
                                        OrderEventType.CANCELED -> OrderStatus.CANCELED
                                        else -> OrderStatus.CREATED
                                    }
                            val currentQty = event.quantity
                            val filledQty =
                                    if (status == OrderStatus.FILLED) currentQty
                                    else if (status == OrderStatus.PARTIALLY_FILLED) currentQty
                                    else 0

                            OrderState(
                                    orderId = event.orderId,
                                    symbol = event.symbol,
                                    status = status,
                                    originalQty = currentQty,
                                    currentQty = currentQty,
                                    filledQty = filledQty,
                                    remainingQty = Math.max(0, currentQty - filledQty),
                                    lastEventTime = event.eventTime
                            )
                        }
                    }
                } else {
                    // Update existing state
                    val updatedState = currentState.copy(lastEventTime = event.eventTime)

                    when (event.eventType) {
                        OrderEventType.CREATED -> {
                            // Ignore or log duplicated CREATED
                            log.info("Duplicate CREATED event for {}", event.orderId)
                        }
                        OrderEventType.MODIFIED -> {
                            updatedState.currentQty = event.quantity
                            updatedState.remainingQty =
                                    Math.max(0, updatedState.currentQty - updatedState.filledQty)
                            // status는 기존 유지
                        }
                        OrderEventType.PARTIALLY_FILLED -> {
                            updatedState.filledQty += event.quantity
                            updatedState.remainingQty =
                                    Math.max(0, updatedState.currentQty - updatedState.filledQty)
                            updatedState.status = OrderStatus.PARTIALLY_FILLED
                        }
                        OrderEventType.FILLED -> {
                            updatedState.filledQty = updatedState.currentQty
                            updatedState.remainingQty = 0
                            updatedState.status = OrderStatus.FILLED
                        }
                        OrderEventType.CANCELED -> {
                            updatedState.remainingQty =
                                    Math.max(0, updatedState.currentQty - updatedState.filledQty)
                            updatedState.status = OrderStatus.CANCELED
                        }
                    }
                    updatedState
                }

        orderState.update(newState)
        out.collect(newState)
    }
}
