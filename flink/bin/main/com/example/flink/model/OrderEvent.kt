package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderEvent(
        @JsonProperty("event_id") val eventId: String? = null,
        @JsonProperty("order_id") val orderId: String? = null,
        @JsonProperty("account_id") val accountId: String? = null,
        @JsonProperty("symbol") val symbol: String? = null,
        @JsonProperty("side") val side: String? = null,
        @JsonProperty("event_type") val eventType: String? = null,
        @JsonProperty("quantity") val quantity: Long? = null,
        @JsonProperty("price") val price: Long? = null,
        @JsonProperty("event_time") val eventTime: String? = null
)
