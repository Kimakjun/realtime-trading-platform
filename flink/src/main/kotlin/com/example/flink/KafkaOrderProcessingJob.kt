package com.example.flink

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.parser.OrderEventParser
import com.example.flink.processor.OrderStateProcessor
import com.example.flink.sink.ClickHouseNormalizedSink
import com.example.flink.sink.ClickHouseOrderStateSink
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("KafkaOrderProcessingJob")
    log.info("Starting KafkaOrderProcessingJob")

    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.parallelism = 1 // Use 1 for local testing

    val source =
            KafkaSource.builder<String>()
                    .setBootstrapServers("kafka:29092")
                    .setTopics("order-events.raw")
                    .setGroupId("flink-order-processor")
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setValueOnlyDeserializer(SimpleStringSchema())
                    .build()

    // Create stream from Kafka Source
    val rawStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-raw-orders")

    // 1. Normalize Event Stream
    val normalizedStream = rawStream.flatMap(OrderEventParser())

    // 2. Sink -> normalized_order_events
    normalizedStream.sinkTo(ClickHouseNormalizedSink.create())

    // 3. Process Order State (Key by OrderId)
    val stateStream =
            normalizedStream
                    .keyBy { it.orderId }
                    .process(OrderStateProcessor())

    // 4. Sink -> order_state_current
    stateStream.sinkTo(ClickHouseOrderStateSink.create())

    // Print for local debugging
    stateStream.print()

    env.execute("kafka-order-state-processing-job")
}
