package com.example.demo.service

import com.example.demo.dto.OrderRequest
import com.example.demo.dto.RawOrderEvent
import com.example.demo.entity.OrderTransaction
import com.example.demo.repository.OrderTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.example.demo.config.ExecutionWebSocketHandler
import com.example.demo.dto.OrderBookResponse

@Service
class OrderService(
    private val orderRepository: OrderTransactionRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val webSocketHandler: ExecutionWebSocketHandler
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)
    private val formatter = DateTimeFormatter.ISO_INSTANT

    @Transactional
    fun submitOrder(request: OrderRequest): OrderTransaction {
        val orderId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()

        val order = OrderTransaction(
            orderId = orderId,
            accountId = request.accountId,
            symbol = request.symbol,
            side = request.side,
            quantity = request.quantity,
            price = request.price,
            status = "NEW"
        )
        val saved = orderRepository.save(order)

        val rawEvent = RawOrderEvent(
            event_id = eventId,
            event_time = saved.createdAt.toInstant(ZoneOffset.UTC).let { formatter.format(it) },
            order_id = orderId,
            account_id = request.accountId,
            symbol = request.symbol,
            event_type = "NEW",
            side = request.side,
            quantity = request.quantity,
            price = request.price,
            payload = "{}" // empty or JSON representation
        )

        val payloadJson = objectMapper.writeValueAsString(rawEvent)
        kafkaTemplate.send("raw_order_events", request.symbol, payloadJson)
        logger.info("Published raw_order_events: {}", payloadJson)

        broadcastOrderBook(request.symbol)

        return saved
    }

    fun getOrderHistory(accountId: String): List<OrderTransaction> {
        return orderRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
    }

    fun getOrderBook(symbol: String): OrderBookResponse {
        val entries = orderRepository.getOrderBook(symbol)
        return OrderBookResponse(symbol, entries)
    }

    fun broadcastOrderBook(symbol: String) {
        val orderBook = getOrderBook(symbol)
        val msg = mapOf("type" to "ORDER_BOOK_UPDATE", "data" to orderBook)
        webSocketHandler.broadcastMessage(objectMapper.writeValueAsString(msg))
    }
}
