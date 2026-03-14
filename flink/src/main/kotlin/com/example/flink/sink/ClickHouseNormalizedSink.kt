package com.example.flink.sink

import com.example.flink.model.NormalizedOrderEvent
import java.time.format.DateTimeFormatter
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction

object ClickHouseNormalizedSink {
    fun create(): SinkFunction<NormalizedOrderEvent> {
        val insertQuery =
                """
            INSERT INTO trading.normalized_order_events (
                event_time, event_id, order_id, account_id, symbol, event_type, quantity, price, raw_payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return JdbcSink.sink(
                insertQuery,
                org.apache.flink.connector.jdbc.JdbcStatementBuilder<NormalizedOrderEvent> {
                        statement,
                        event ->
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    statement.setString(1, event.eventTime.format(formatter))
                    statement.setString(2, event.eventId)
                    statement.setString(3, event.orderId)
                    statement.setString(4, event.accountId)
                    statement.setString(5, event.symbol)
                    statement.setString(6, event.eventType.name)
                    statement.setLong(7, event.quantity.toLong())
                    statement.setLong(8, event.price)
                    statement.setString(9, event.rawPayload)
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(1)
                        .withBatchIntervalMs(0)
                        .withMaxRetries(3)
                        .build(),
                JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:clickhouse://clickhouse:8123/trading")
                        .withDriverName("ru.yandex.clickhouse.ClickHouseDriver")
                        .build()
        )
    }
}
