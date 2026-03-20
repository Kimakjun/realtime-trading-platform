package com.example.simulator

import com.example.flink.model.OrderEvent
import com.example.flink.model.OrderEventType
import java.time.Instant
import java.util.*
import kotlin.random.Random

class ScenarioGenerator {
    // 메모리에 현재 진행 중인 주문 목록을 유지 (주문번호 기준)
    private val activeOrders = mutableMapOf<String, ActiveOrder>()

    // 거래 가능한 심볼 목록
    private val symbols = listOf("BTC/USD", "ETH/USD", "XRP/USD", "SOL/USD", "ADA/USD")

    // 무작위 계좌 ID 생성용
    private val accountIds = (1..50).map { "ACC_${it.toString().padStart(5, '0')}" }

    data class ActiveOrder(
            val originalEvent: OrderEvent,
            var currentQty: Long,
            var filledQty: Long,
            val creationTimeMs: Long
    )

    fun nextEvent(): OrderEvent {
        val now = Instant.now()
        val nowIso = now.toString() // e.g. "2023-10-27T10:00:00Z"
        val eventId = UUID.randomUUID().toString()

        // 1. 진행 중인 주문이 하나도 없거나, 낮은 확률(20%)로 무조건 새 주문 생성
        if (activeOrders.isEmpty() || Random.nextDouble() < 0.2) {
            return createNewOrder(nowIso, eventId)
        }

        // 2. 기존 주문 상태 변경 시나리오
        // 임의의 진행 중인 주문 픽
        val targetOrderId = activeOrders.keys.random()
        val activeOrder = activeOrders[targetOrderId]!!
        val remainingQty = activeOrder.currentQty - activeOrder.filledQty

        // 너무 오래된 주문은 취소할 확률 높임 (30초 이상 된 경우)
        val ageMs = now.toEpochMilli() - activeOrder.creationTimeMs
        val isStale = ageMs > 30_000

        val r = Random.nextDouble()

        val newEvent =
                when {
                    isStale || r < 0.1 -> { // 10% 확률 또는 오래된 주문 -> 취소
                        val event =
                                OrderEvent(
                                        eventId = eventId,
                                        orderId = targetOrderId,
                                        accountId = activeOrder.originalEvent.accountId,
                                        symbol = activeOrder.originalEvent.symbol,
                                        eventType = OrderEventType.CANCELED.name,
                                        quantity = remainingQty,
                                        price = activeOrder.originalEvent.price,
                                        eventTime = nowIso
                                )
                        activeOrders.remove(targetOrderId) // 완료된 주문 삭제
                        event
                    }
                    r < 0.15 -> { // 5% 확률 -> 수량 변경 (MODIFIED)
                        val newQty = activeOrder.currentQty + Random.nextLong(-10, 50)
                        if (newQty <= activeOrder.filledQty) {
                            // 수량을 이미 체결된 양보다 줄일 수 없음 -> CANCELED 취급
                            activeOrders.remove(targetOrderId)
                            OrderEvent(
                                    eventId = eventId,
                                    orderId = targetOrderId,
                                    accountId = activeOrder.originalEvent.accountId,
                                    symbol = activeOrder.originalEvent.symbol,
                                    eventType = OrderEventType.CANCELED.name,
                                    quantity = remainingQty,
                                    price = activeOrder.originalEvent.price,
                                    eventTime = nowIso
                            )
                        } else {
                            activeOrder.currentQty = newQty
                            OrderEvent(
                                    eventId = eventId,
                                    orderId = targetOrderId,
                                    accountId = activeOrder.originalEvent.accountId,
                                    symbol = activeOrder.originalEvent.symbol,
                                    eventType = OrderEventType.MODIFIED.name,
                                    quantity = newQty,
                                    price = activeOrder.originalEvent.price,
                                    eventTime = nowIso
                            )
                        }
                    }
                    r < 0.6 -> { // 45% 확률 -> 전량 체결
                        val fillEvent =
                                OrderEvent(
                                        eventId = eventId,
                                        orderId = targetOrderId,
                                        accountId = activeOrder.originalEvent.accountId,
                                        symbol = activeOrder.originalEvent.symbol,
                                        eventType = OrderEventType.FILLED.name,
                                        quantity = remainingQty,
                                        price = activeOrder.originalEvent.price,
                                        eventTime = nowIso
                                )
                        activeOrders.remove(targetOrderId) // 완료된 주문 삭제
                        fillEvent
                    }
                    else -> { // 나머지 40% 확률 -> 부분 체결
                        val partialFillQty =
                                if (remainingQty <= 1L) 1L else Random.nextLong(1, remainingQty + 1)
                        if (partialFillQty >= remainingQty) { // 남은 수량을 다 채웠다면 FILLED
                            activeOrders.remove(targetOrderId)
                            OrderEvent(
                                    eventId = eventId,
                                    orderId = targetOrderId,
                                    accountId = activeOrder.originalEvent.accountId,
                                    symbol = activeOrder.originalEvent.symbol,
                                    eventType = OrderEventType.FILLED.name,
                                    quantity = remainingQty,
                                    price = activeOrder.originalEvent.price,
                                    eventTime = nowIso
                            )
                        } else {
                            activeOrder.filledQty += partialFillQty
                            OrderEvent(
                                    eventId = eventId,
                                    orderId = targetOrderId,
                                    accountId = activeOrder.originalEvent.accountId,
                                    symbol = activeOrder.originalEvent.symbol,
                                    eventType = OrderEventType.PARTIALLY_FILLED.name,
                                    quantity = partialFillQty,
                                    price = activeOrder.originalEvent.price,
                                    eventTime = nowIso
                            )
                        }
                    }
                }

        return newEvent
    }

    private fun createNewOrder(nowIso: String, eventId: String): OrderEvent {
        val orderId = "ORD_${UUID.randomUUID().toString().take(8)}"
        val symbol = symbols.random()
        val accountId = accountIds.random()
        val qty = Random.nextLong(10, 1000)

        // 가격: 대략적인 기준 가격 (시뮬레이션 용)
        val basePrice =
                when (symbol) {
                    "BTC/USD" -> 60_000L
                    "ETH/USD" -> 3_000L
                    "XRP/USD" -> 1L
                    "SOL/USD" -> 150L
                    "ADA/USD" -> 1L
                    else -> 100L
                }
        val offset = Math.max(1L, basePrice / 10)
        val price = basePrice + Random.nextLong(-offset, offset + 1)

        val newEvent =
                OrderEvent(
                        eventId = eventId,
                        orderId = orderId,
                        accountId = accountId,
                        symbol = symbol,
                        eventType = OrderEventType.CREATED.name,
                        quantity = qty,
                        price = price,
                        eventTime = nowIso
                )

        activeOrders[orderId] =
                ActiveOrder(
                        originalEvent = newEvent,
                        currentQty = qty,
                        filledQty = 0L,
                        creationTimeMs = Instant.now().toEpochMilli()
                )

        return newEvent
    }
}
