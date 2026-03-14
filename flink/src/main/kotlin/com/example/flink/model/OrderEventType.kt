package com.example.flink.model

enum class OrderEventType {
    CREATED,
    MODIFIED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED;

    companion object {
        fun from(value: String): OrderEventType =
                entries.firstOrNull { it.name == value.uppercase() }
                        ?: throw IllegalArgumentException("Unknown event type: $value")
    }
}
