package com.example.flink.parser

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEvent
import com.example.flink.model.OrderEventType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

class OrderEventParser : FlatMapFunction<String, NormalizedOrderEvent> {
    private val log = LoggerFactory.getLogger(OrderEventParser::class.java)

    @Transient private var mapper: ObjectMapper? = null

    override fun flatMap(value: String, out: Collector<NormalizedOrderEvent>) {
        if (mapper == null) {
            mapper = jacksonObjectMapper()
        }

        try {
            val event = mapper!!.readValue<OrderEvent>(value)

            // Validation 1: orderId 필수
            val orderId = event.orderId
            if (orderId.isNullOrBlank()) {
                log.warn("Dropped event due to missing orderId: {}", value)
                return
            }

            // Validation 2: eventType 확인 및 Enum 변환
            val eventTypeStr = event.eventType
            if (eventTypeStr.isNullOrBlank()) {
                log.warn("Dropped event due to missing eventType: {}", value)
                return
            }
            val eventType =
                    try {
                        OrderEventType.from(eventTypeStr)
                    } catch (e: IllegalArgumentException) {
                        log.warn("Dropped event due to invalid eventType: {}", value)
                        return
                    }

            // Validation 3: quantity > 0
            val quantity = event.quantity?.toInt() ?: 0
            if (quantity <= 0) {
                log.warn("Dropped event due to invalid quantity (<= 0): {}", value)
                return
            }

            // Validation 4: price >= 0 (문서에는 < 0 이면 drop 이라고 명시됨)
            val price = event.price ?: 0L
            if (price < 0) {
                log.warn("Dropped event due to invalid price (< 0): {}", value)
                return
            }

            val eventTimeStr = event.eventTime
            val eventTime =
                    if (!eventTimeStr.isNullOrBlank()) {
                        try {
                            LocalDateTime.parse(
                                    eventTimeStr,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            )
                        } catch (e: Exception) {
                            LocalDateTime.now()
                        }
                    } else {
                        LocalDateTime.now()
                    }

            val normalizedEvent =
                    NormalizedOrderEvent(
                            eventTime = eventTime,
                            eventId = event.eventId ?: "unknown-${System.currentTimeMillis()}",
                            orderId = orderId,
                            accountId = event.accountId ?: "unknown-account",
                            symbol = event.symbol ?: "unknown-symbol",
                            eventType = eventType,
                            quantity = quantity,
                            price = price,
                            rawPayload = value
                    )
            out.collect(normalizedEvent)
        } catch (e: Exception) {
            log.warn("Failed to parse JSON: {}", value, e)
        }
    }
}
