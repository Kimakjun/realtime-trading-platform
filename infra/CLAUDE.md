# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Overview

Docker Compose 기반 인프라 구성. 각 서비스별 독립 compose.yml 파일 운영.

## Service Compose Files

| Service | Compose Path | Network |
|---------|-------------|---------|
| Kafka (KRaft) | `kafka/compose.yml` | kafka_network |
| PostgreSQL 15 | `postgres/compose.yml` | default |
| ClickHouse | `clickhouse/compose.yml` | clickhouse_network |
| Flink 1.20.3 | `flink/compose.yml` | kafka_network, clickhouse_network |
| Grafana | `grafana/compose.yml` | clickhouse_network |

## Orchestration Scripts

```bash
bash scripts/start-all.sh    # 전체 기동 (infra → build → job submit → app start)
bash scripts/stop-all.sh     # 전체 중지 (kill ports + compose down)
bash scripts/reset-data.sh   # 데이터 초기화
```

`start-all.sh` 순서: Kafka/PG/CH/Flink/Grafana compose up → health check 대기 → Flink shadowJar 빌드 → job submit → API Gateway bootRun → Frontend dev

## Kafka Setup

- KRaft mode (Zookeeper 없음), auto.create.topics=false
- `kafka/scripts/init-topics.sh`: 토픽 자동 생성 (raw_order_events, executed_orders, market-ticks.raw, dlq.order-events)
- `kafka/scripts/smoke-test.sh`: 연결 확인

## ClickHouse Schema

`clickhouse/init/001_init.sql`:
- `raw_order_events` (MergeTree)
- `normalized_order_events` (MergeTree)
- `order_state_current` (ReplacingMergeTree — last_event_time 기준 중복 제거)

## Flink Cluster Config

`flink/conf/config.yaml`: parallelism 1, TaskManager slots 2, checkpoint file-based
`flink/scripts/submit-job.sh`: fat JAR을 JobManager에 제출

## Docker Networks

Flink은 `kafka_network`와 `clickhouse_network` 모두에 연결. 서비스 간 internal 통신은 Docker DNS (container name) 사용.
