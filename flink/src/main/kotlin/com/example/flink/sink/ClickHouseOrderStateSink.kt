package com.example.flink.sink

import com.example.flink.model.OrderState
import java.time.format.DateTimeFormatter
import org.apache.flink.connector.jdbc.JdbcConnectionOptions
import org.apache.flink.connector.jdbc.JdbcExecutionOptions
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction

object ClickHouseOrderStateSink {
    fun create(): SinkFunction<OrderState> {
        val insertQuery =
                """
            INSERT INTO trading.order_state_current (
                order_id, symbol, status, original_qty, current_qty, filled_qty, remaining_qty, last_event_time
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return JdbcSink.sink(
                insertQuery,
                org.apache.flink.connector.jdbc.JdbcStatementBuilder<OrderState> { statement, state
                    ->
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    statement.setString(1, state.orderId)
                    statement.setString(2, state.symbol)
                    statement.setString(3, state.status.name)
                    statement.setLong(4, state.originalQty.toLong())
                    statement.setLong(5, state.currentQty.toLong())
                    statement.setLong(6, state.filledQty.toLong())
                    statement.setLong(7, state.remainingQty.toLong())
                    statement.setString(8, state.lastEventTime.format(formatter))
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
