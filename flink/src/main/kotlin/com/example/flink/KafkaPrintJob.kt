package com.example.flink

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

fun main() {
    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.setParallelism(1)

    val source =
            KafkaSource.builder<String>()
                    .setBootstrapServers("kafka:29092")
                    .setTopics("order-events.raw")
                    .setGroupId("flink-kafka-reader")
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setValueOnlyDeserializer(SimpleStringSchema())
                    .build()

    val stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-source")

    stream.map {}

    stream.print()

    env.execute("kafka-print-job")
}
