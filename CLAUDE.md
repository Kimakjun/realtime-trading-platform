# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

실시간 주문 처리 트레이딩 플랫폼. Event-Driven CQRS 아키텍처 기반 모노레포.

## Architecture

```
Frontend (React/Vite) :5173
    ↓ HTTP POST / WebSocket
API Gateway (Spring Boot) :8082
    ↓ Kafka Producer → raw_order_events topic
Kafka (KRaft) :9092
    ↓ Kafka Consumer
Flink Cluster :8081
    ├─ OrderEventParser (validation/normalization)
    ├─ MatchingEngineProcessor (keyBy symbol, price crossing)
    └─ OrderStateProcessor (keyBy orderId, state reconstruction)
    ↓ Kafka Producer → executed_orders topic
API Gateway (KafkaListener)
    ├─ PostgreSQL :5433 (transactional write)
    └─ WebSocket push → Frontend
ClickHouse :8123 (analytics sink from Flink)
Grafana :3001 (ClickHouse dashboard)
```

**Data Flow**: REST → PostgreSQL + Kafka → Flink processing → Kafka → API Gateway consumes → DB update + WebSocket push

## Monorepo Structure

| Directory | Role | Tech Stack |
|-----------|------|------------|
| `flink/` | Stream processing (order parsing, matching, state) | Kotlin, Flink 1.20.3, Gradle |
| `api-gateway/` | REST API + WebSocket + Kafka consumer/producer | Kotlin, Spring Boot 4.0.4, Gradle |
| `frontend/` | Trading UI | React 19, TypeScript, Vite, Jotai, Ant Design |
| `infra/` | Docker Compose configs + scripts | Kafka, PostgreSQL, ClickHouse, Flink, Grafana |

## Full Stack Commands

```bash
# Start/stop entire platform
bash infra/scripts/start-all.sh
bash infra/scripts/stop-all.sh
bash infra/scripts/reset-data.sh
```

## Kafka Topics

| Topic | Partitions | Producer | Consumer |
|-------|-----------|----------|----------|
| `raw_order_events` | 3 | API Gateway | Flink |
| `executed_orders` | 3 | Flink MatchingEngine | API Gateway |
| `market-ticks.raw` | 3 | - | - |
| `dlq.order-events` | 1 | DLQ | - |

## Key Design Decisions

- **Eventual Consistency**: Frontend does optimistic UI update + 300ms fallback refetch after WebSocket message
- **Matching Engine**: Keyed by symbol, maintains separate buy/sell order books in Flink ListState, maker's price used for execution
- **Order State**: Flink ValueState with checkpointing, handles degraded states (non-CREATED first events)
- **CQRS Split**: Writes go through Kafka, reads from PostgreSQL, analytics from ClickHouse (ReplacingMergeTree)

## Port Map

| Service | Port |
|---------|------|
| Kafka | 9092 (external), 29092 (internal) |
| Kafka UI | 8080 |
| Flink Web UI | 8081 |
| API Gateway | 8082 |
| ClickHouse HTTP | 8123 |
| ClickHouse Native | 9000 |
| ClickHouse UI (Tabix) | 8088 |
| Grafana | 3001 |
| Frontend (Vite) | 5173 |
| PostgreSQL | 5433 |
