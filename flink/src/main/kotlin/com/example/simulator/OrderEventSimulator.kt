package com.example.simulator

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("OrderEventSimulator")
    val mapper = jacksonObjectMapper()

    // Kafka Producer Configuration
    val props = Properties()
    props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    props.setProperty(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer::class.java.name
    )

    val producer = KafkaProducer<String, String>(props)
    val topic = "order-events.raw"

    val generator = ScenarioGenerator()
    val targetEventsPerSecond = 5 // 초당 5개 이벤트 발생 (조절 가능)
    val sleepMs = 1000L / targetEventsPerSecond

    log.info("🚀 Starting Order Event Simulator. Target: $targetEventsPerSecond events/sec")

    Runtime.getRuntime()
            .addShutdownHook(
                    Thread {
                        log.info("Shutting down simulator...")
                        producer.close()
                    }
            )

    try {
        while (true) {
            val event = generator.nextEvent()

            // Convert to JSON and send to Kafka
            val jsonString = mapper.writeValueAsString(event)
            val record = ProducerRecord(topic, event.orderId, jsonString)

            producer.send(record) { metadata, exception ->
                if (exception != null) {
                    log.error("Error sending message to Kafka", exception)
                } else {
                    log.info(
                            "Sent event: ${event.eventType} for order: ${event.orderId} at offset: ${metadata.offset()}"
                    )
                }
            }

            Thread.sleep(sleepMs)
        }
    } catch (e: InterruptedException) {
        log.info("Simulator interrupted")
    } finally {
        producer.close()
    }
}
