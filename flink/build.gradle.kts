plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenCentral()
}

val flinkVersion = "1.20.3"
val kafkaConnectorVersion = "3.4.0-1.20"
val jdbcConnectorVersion = "3.2.0-1.19"
val clickhouseJdbcVersion = "0.6.0"
val jacksonVersion = "2.15.2"

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-clients:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kafka:$kafkaConnectorVersion")
    implementation("org.apache.flink:flink-connector-jdbc:$jdbcConnectorVersion")

    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    testImplementation(kotlin("test"))

    // Flink test utilities (MiniCluster)
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.apache.flink:flink-runtime:$flinkVersion")
    testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion:tests")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.example.flink.KafkaOrderProcessingJobKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(
        org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    )
}

tasks.jar {
    enabled = false
    manifest {
        attributes["Main-Class"] = "com.example.flink.KafkaOrderProcessingJobKt"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.distZip {
    dependsOn(tasks.shadowJar)
}

tasks.distTar {
    dependsOn(tasks.shadowJar)
}

tasks.startScripts {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.time=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED"
    )
}

tasks.register<JavaExec>("runSimulator") {
    group = "application"
    description = "Runs the Order Event Simulator to generate realistic order flows to Kafka."
    mainClass.set("com.example.simulator.OrderEventSimulatorKt")
    classpath = sourceSets["main"].runtimeClasspath
}