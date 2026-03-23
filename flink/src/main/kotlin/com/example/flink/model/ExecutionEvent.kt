package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ExecutionEvent(
    @JsonProperty("execution_id") val executionId: String,
    @JsonProperty("order_id") val orderId: String,
    @JsonProperty("account_id") val accountId: String,
    @JsonProperty("symbol") val symbol: String,
    @JsonProperty("side") val side: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("price") val price: Long,
    @JsonProperty("timestamp") val timestamp: String
)
