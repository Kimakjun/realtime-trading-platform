package com.example.flink.model

data class NormalizedOrderEvent(
        val eventTime: java.time.LocalDateTime,
        val eventId: String,
        val orderId: String,
        val accountId: String,
        val symbol: String,
        val eventType: OrderEventType,
        val quantity: Int,
        val price: Long,
        val rawPayload: String
)
