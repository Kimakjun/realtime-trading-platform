# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Overview

Apache Flink 스트림 처리 애플리케이션. Kafka에서 주문 이벤트를 소비하여 파싱, 매칭, 상태 관리 후 ClickHouse와 Kafka로 출력.

## Build & Test

```bash
./gradlew clean shadowJar      # Fat JAR 빌드 (build/libs/)
./gradlew test                  # 테스트 실행
./gradlew runSimulator          # 주문 시뮬레이터 실행 (Kafka 필요)
```

## Tech Stack

- Kotlin + Java 17 (jvmToolchain)
- Flink 1.20.3, Kafka Connector 3.4.0-1.20
- ClickHouse JDBC, Jackson Kotlin
- Shadow plugin for fat JAR

## Pipeline Architecture

```
KafkaSource("raw_order_events")
  → OrderEventParser (FlatMap: validation + normalization)
  → MatchingEngineProcessor (KeyBy symbol: buy/sell order book matching)
      ├─ Main output: ExecutionEvent JSON → KafkaSink("executed_orders")
      └─ Side output: NormalizedOrderEvent → OrderStateProcessor
  → OrderStateProcessor (KeyBy orderId: state reconstruction)
      → ClickHouse sinks (normalized events + order state)
```

## Key Processing Logic

- **OrderEventParser**: JSON → NormalizedOrderEvent. orderId/eventType 필수, quantity > 0, price >= 0. 유효하지 않은 이벤트는 로그 후 drop (예외 없음)
- **MatchingEngineProcessor**: symbol별 ListState로 buy/sell book 유지. BUY.price >= SELL.price일 때 체결. maker's price 사용
- **OrderStateProcessor**: orderId별 ValueState<OrderState>. 첫 이벤트로 생성, 이후 상태 전이. 비정상 첫 이벤트는 경고 로그

## Event Types

`CREATED` | `MODIFIED` | `PARTIALLY_FILLED` | `FILLED` | `CANCELED` ("NEW" → CREATED 매핑)

## Flink State & Checkpointing

- MatchingEngine: `ListState` (buy/sell books per symbol)
- OrderState: `ValueState<OrderState>` (per orderId)
- Checkpoint 설정: `infra/flink/conf/config.yaml` (file-based)

## Test Patterns

- `OrderEventParserTest`: 직접 FlatMapFunction 호출, Collector mock
- `OrderStateProcessorTest`: Flink MiniCluster 사용
- `MatchingEngineProcessorTest`: 매칭 로직 단위 테스트
- `OrderProcessingPipelineTest`: E2E 파이프라인 테스트
