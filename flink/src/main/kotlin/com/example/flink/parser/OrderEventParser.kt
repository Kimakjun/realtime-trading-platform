package com.example.flink.parser

import com.example.flink.model.OrderEvent
import com.example.flink.model.RawOrderEventRow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

class OrderEventParser : FlatMapFunction<String, RawOrderEventRow> {
    private val log = LoggerFactory.getLogger(OrderEventParser::class.java)

    @Transient private var mapper: ObjectMapper? = null

    override fun flatMap(value: String, out: Collector<RawOrderEventRow>) {
        if (mapper == null) {
            mapper = jacksonObjectMapper()
        }

        try {
            val event = mapper!!.readValue<OrderEvent>(value)

            val eventTime =
                    event.eventTime
                            ?: LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val row =
                    RawOrderEventRow(
                            eventTime = eventTime,
                            eventId = event.eventId ?: "unknown",
                            orderId = event.orderId ?: "unknown",
                            accountId = event.accountId ?: "unknown",
                            symbol = event.symbol ?: "unknown",
                            eventType = event.eventType ?: "unknown",
                            quantity = event.quantity ?: 0L,
                            price = event.price ?: 0L,
                            payload = value
                    )
            out.collect(row)
        } catch (e: Exception) {
            log.warn("Failed to parse JSON: {}", value, e)
        }
    }
}
