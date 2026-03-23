package com.example.demo.service

import com.example.demo.config.ExecutionWebSocketHandler
import com.example.demo.dto.ExecutionEvent
import com.example.demo.entity.ExecutionHistory
import com.example.demo.repository.ExecutionHistoryRepository
import com.example.demo.repository.OrderTransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ExecutionService(
    private val executionRepository: ExecutionHistoryRepository,
    private val orderRepository: OrderTransactionRepository,
    private val objectMapper: ObjectMapper,
    private val webSocketHandler: ExecutionWebSocketHandler
) {
    private val logger = LoggerFactory.getLogger(ExecutionService::class.java)

    @KafkaListener(topics = ["executed_orders"], groupId = "api-gateway-group")
    @Transactional
    fun consumeExecutionEvent(message: String) {
        logger.info("Received executed_orders: {}", message)
        try {
            val event = objectMapper.readValue(message, ExecutionEvent::class.java)

            // Save Execution History
            val history = ExecutionHistory(
                executionId = event.execution_id,
                orderId = event.order_id,
                accountId = event.account_id,
                symbol = event.symbol,
                side = event.side,
                price = event.price,
                quantity = event.quantity,
                executedAt = Instant.parse(event.timestamp).atZone(ZoneOffset.UTC).toLocalDateTime()
            )
            executionRepository.save(history)

            // Update Order
            val order = orderRepository.findByOrderId(event.order_id)
            if (order != null) {
                order.filledQuantity += event.quantity
                if (order.filledQuantity >= order.quantity) {
                    order.status = "FILLED"
                } else if (order.filledQuantity > 0) {
                    order.status = "PARTIALLY_FILLED"
                }
                order.updatedAt = LocalDateTime.now()
                orderRepository.save(order)
            }

            // Push to WebSocket
            val wsMessage = """{"type":"EXECUTION","data":$message}"""
            webSocketHandler.pushMessageToAccount(event.account_id, wsMessage)

            // Broadcast Order Book Update
            val entries = orderRepository.getOrderBook(event.symbol)
            val orderBookResponse = com.example.demo.dto.OrderBookResponse(event.symbol, entries)
            val obMsg = mapOf("type" to "ORDER_BOOK_UPDATE", "data" to orderBookResponse)
            webSocketHandler.broadcastMessage(objectMapper.writeValueAsString(obMsg))

        } catch (e: Exception) {
            logger.error("Failed to process execution event", e)
        }
    }

    fun getExecutionHistory(accountId: String): List<ExecutionHistory> {
        return executionRepository.findByAccountIdOrderByExecutedAtDesc(accountId)
    }
}
