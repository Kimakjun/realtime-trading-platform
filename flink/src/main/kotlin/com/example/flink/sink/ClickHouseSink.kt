package com.example.flink.sink

import com.example.flink.model.RawOrderEventRow
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction

object ClickHouseSink {
    fun create(): SinkFunction<RawOrderEventRow> {
        val insertQuery =
                """
            INSERT INTO trading.raw_order_events (
                event_time, event_id, order_id, account_id, symbol, event_type, quantity, price, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return JdbcSink.sink(
                insertQuery,
                { statement, row ->
                    statement.setString(1, row.eventTime)
                    statement.setString(2, row.eventId)
                    statement.setString(3, row.orderId)
                    statement.setString(4, row.accountId)
                    statement.setString(5, row.symbol)
                    statement.setString(6, row.eventType)
                    statement.setLong(7, row.quantity)
                    statement.setLong(8, row.price)
                    statement.setString(9, row.payload)
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(1) // Set to 1 for quick end-to-end testing
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
