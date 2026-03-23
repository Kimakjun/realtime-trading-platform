# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Overview

Spring Boot 기반 API Gateway. REST API로 주문 접수, Kafka로 이벤트 발행, 체결 결과를 Kafka에서 소비하여 PostgreSQL 저장 + WebSocket 푸시.

## Build & Run

```bash
./gradlew bootRun    # 서버 시작 (port 8082)
./gradlew test       # 테스트 실행
./gradlew build      # 빌드
```

## Tech Stack

- Kotlin + Spring Boot 4.0.4
- Spring Data JPA + PostgreSQL
- Spring Kafka (producer/consumer)
- Spring WebSocket

## API Endpoints

```
POST   /api/v1/orders                    # 주문 제출
GET    /api/v1/orders/history?accountId=  # 주문 이력
GET    /api/v1/orders/executions?accountId= # 체결 이력
GET    /api/v1/orders/book?symbol=        # 호가창 (order book)
```

## WebSocket

- Endpoint: `ws://localhost:8082/ws/orders?accountId={id}`
- Message types: `EXECUTION` (체결 알림), `ORDER_BOOK_UPDATE` (호가 갱신)
- accountId별 세션 관리, targeted + broadcast 전송

## Data Flow

1. `OrderService.submitOrder()` → PostgreSQL 저장 + Kafka `raw_order_events` 발행
2. `ExecutionService` @KafkaListener(`executed_orders`) → DB 업데이트 + WebSocket 푸시
3. 체결 시 OrderTransaction status 업데이트 + ExecutionHistory 생성

## Database

- **PostgreSQL**: `trading_gateway` (port 5433)
- Credentials: `trading_user` / `trading_password`
- Entities: `OrderTransaction` (주문), `ExecutionHistory` (체결)

## Configuration

`src/main/resources/application.yml` — DB, Kafka, logging 설정
