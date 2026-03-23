package com.example.demo.dto

data class ExecutionEvent(
    val execution_id: String,
    val order_id: String,
    val account_id: String,
    val symbol: String,
    val side: String,
    val quantity: Int,
    val price: Long,
    val timestamp: String
)
