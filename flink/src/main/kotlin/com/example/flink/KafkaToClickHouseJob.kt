package com.example.flink

import com.example.flink.parser.OrderEventParser
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("KafkaToClickHouseJob")
    log.info("Starting KafkaToClickHouseJob")

    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.parallelism = 1

    val source =
            KafkaSource.builder<String>()
                    .setBootstrapServers("kafka:29092")
                    .setTopics("order-events.raw")
                    .setGroupId("flink-kafka-reader")
                    .setStartingOffsets(OffsetsInitializer.earliest())
                    .setValueOnlyDeserializer(SimpleStringSchema())
                    .build()

    val stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-source")

    // 1. JSON 문자열 파싱 -> RawOrderEventRow
    val parsedStream = stream.flatMap(OrderEventParser())

    // 2. 디버깅 등을 위해 로컬에서 확인할 수 있도록 출력
    parsedStream.print()

    // 3. ClickHouse Sink 적용
    parsedStream.addSink(com.example.flink.sink.ClickHouseNormalizedSink.create())

    env.execute("kafka-to-clickhouse-job")
}
