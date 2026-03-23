#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${RED}🗑️  전체 파이프라인 데이터 리셋을 시작합니다...${NC}"
echo -e "${YELLOW}   PostgreSQL, ClickHouse, Kafka 토픽의 모든 데이터가 삭제됩니다.${NC}"
echo ""

# ── 1. PostgreSQL ──
echo -e "${CYAN}[1/3] PostgreSQL 테이블 초기화...${NC}"
if docker ps --format '{{.Names}}' | grep -q 'local-postgres'; then
  docker exec local-postgres psql -U trading_user -d trading_gateway -c "
    TRUNCATE TABLE execution_histories CASCADE;
    TRUNCATE TABLE order_transactions CASCADE;
  " 2>/dev/null
  echo -e "${GREEN}  ✅ order_transactions, execution_histories 초기화 완료${NC}"
else
  echo -e "${YELLOW}  ⚠️  PostgreSQL 컨테이너가 실행중이 아닙니다. 건너뜁니다.${NC}"
fi

# ── 2. ClickHouse ──
echo -e "${CYAN}[2/3] ClickHouse 테이블 초기화...${NC}"
if docker ps --format '{{.Names}}' | grep -q 'local-clickhouse'; then
  docker exec local-clickhouse clickhouse-client --query "TRUNCATE TABLE IF EXISTS trading.raw_order_events"
  docker exec local-clickhouse clickhouse-client --query "TRUNCATE TABLE IF EXISTS trading.normalized_order_events"
  docker exec local-clickhouse clickhouse-client --query "TRUNCATE TABLE IF EXISTS trading.order_state_current"
  echo -e "${GREEN}  ✅ raw_order_events, normalized_order_events, order_state_current 초기화 완료${NC}"
else
  echo -e "${YELLOW}  ⚠️  ClickHouse 컨테이너가 실행중이 아닙니다. 건너뜁니다.${NC}"
fi

# ── 3. Kafka Topics ──
echo -e "${CYAN}[3/3] Kafka 토픽 데이터 초기화...${NC}"
if docker ps --format '{{.Names}}' | grep -q 'local-kafka'; then
  TOPICS=("raw_order_events" "executed_orders" "market-ticks.raw" "dlq.order-events")
  for TOPIC in "${TOPICS[@]}"; do
    # 토픽 삭제 후 재생성 (가장 확실한 방법)
    docker exec local-kafka kafka-topics --bootstrap-server localhost:29092 --delete --topic "$TOPIC" 2>/dev/null || true
  done
  sleep 2
  for TOPIC in "${TOPICS[@]}"; do
    PARTITIONS=3
    if [ "$TOPIC" = "dlq.order-events" ]; then
      PARTITIONS=1
    fi
    docker exec local-kafka kafka-topics --bootstrap-server localhost:29092 \
      --create --if-not-exists --topic "$TOPIC" \
      --partitions $PARTITIONS --replication-factor 1 2>/dev/null || true
  done
  echo -e "${GREEN}  ✅ Kafka 토픽 4개 삭제 후 재생성 완료${NC}"
else
  echo -e "${YELLOW}  ⚠️  Kafka 컨테이너가 실행중이 아닙니다. 건너뜁니다.${NC}"
fi

echo ""
echo -e "${GREEN}✅ 전체 데이터 리셋 완료!${NC}"
echo -e "${YELLOW}💡 Flink Job이 실행 중이라면 재시작해야 상태가 초기화됩니다:${NC}"
echo "   docker compose -f infra/flink/compose.yml restart"
echo "   bash infra/flink/scripts/submit-job.sh"
