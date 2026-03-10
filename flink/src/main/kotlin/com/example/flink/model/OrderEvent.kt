package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderEvent(
        @JsonProperty("eventId") val eventId: String? = null,
        @JsonProperty("orderId") val orderId: String? = null,
        @JsonProperty("accountId") val accountId: String? = null,
        @JsonProperty("symbol") val symbol: String? = null,
        @JsonProperty("type") val eventType: String? = null,
        @JsonProperty("qty") val quantity: Long? = null,
        @JsonProperty("price") val price: Long? = null,
        @JsonProperty("eventTime") val eventTime: String? = null
)
