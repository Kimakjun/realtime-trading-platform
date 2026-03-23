package com.example.demo.dto

data class OrderBookEntry(
    val side: String,
    val price: Long,
    val remainingQuantity: Long
)

data class OrderBookResponse(
    val symbol: String,
    val entries: List<OrderBookEntry>
)
