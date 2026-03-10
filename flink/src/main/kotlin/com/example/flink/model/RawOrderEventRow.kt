package com.example.flink.model

data class RawOrderEventRow(
        val eventTime: String,
        val eventId: String,
        val orderId: String,
        val accountId: String,
        val symbol: String,
        val eventType: String,
        val quantity: Long,
        val price: Long,
        val payload: String
)
