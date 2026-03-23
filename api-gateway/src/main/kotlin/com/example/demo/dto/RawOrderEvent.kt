package com.example.demo.dto

import java.time.LocalDateTime

data class RawOrderEvent(
    val event_id: String,
    val event_time: String, // ISO format
    val order_id: String,
    val account_id: String,
    val symbol: String,
    val event_type: String, // e.g. "NEW"
    val side: String, // "BUY" or "SELL"
    val quantity: Int,
    val price: Long,
    val payload: String
)
