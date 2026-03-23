package com.example.flink.model

data class OrderState(
        val orderId: String,
        val symbol: String,
        var status: OrderStatus,
        var originalQty: Int,
        var currentQty: Int,
        var filledQty: Int,
        var remainingQty: Int,
        var lastEventTime: java.time.LocalDateTime
)
