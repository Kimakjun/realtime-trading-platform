#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${YELLOW}🛑 Stopping all services...${NC}"
echo ""

# ── 1. Frontend (port 5173) ──
echo -e "${RED}[1/6] Stopping Frontend...${NC}"
FE_PIDS=$(lsof -ti:5173 2>/dev/null || true)
if [ -n "$FE_PIDS" ]; then
  echo "$FE_PIDS" | xargs kill -9 2>/dev/null || true
  echo -e "${GREEN}  ✅ Frontend stopped (port 5173)${NC}"
else
  echo -e "${YELLOW}  ⚠️  Frontend not running on port 5173${NC}"
fi
rm -f /tmp/frontend.pid

# ── 2. API Gateway (port 8082) ──
echo -e "${RED}[2/6] Stopping API Gateway...${NC}"
GW_PIDS=$(lsof -ti:8082 2>/dev/null || true)
if [ -n "$GW_PIDS" ]; then
  echo "$GW_PIDS" | xargs kill -9 2>/dev/null || true
  echo -e "${GREEN}  ✅ API Gateway stopped (port 8082)${NC}"
else
  echo -e "${YELLOW}  ⚠️  API Gateway not running on port 8082${NC}"
fi
pkill -f "api-gateway.*bootRun" 2>/dev/null || true
rm -f /tmp/api-gateway.pid

# ── 3. Grafana ──
echo -e "${RED}[3/6] Stopping Grafana...${NC}"
docker compose -f "$INFRA_DIR/grafana/compose.yml" down

# ── 4. Flink ──
echo -e "${RED}[4/6] Stopping Flink...${NC}"
docker compose -f "$INFRA_DIR/flink/compose.yml" down

# ── 5. ClickHouse ──
echo -e "${RED}[5/6] Stopping ClickHouse...${NC}"
docker compose -f "$INFRA_DIR/clickhouse/compose.yml" down

# ── 6. Kafka & PostgreSQL ──
echo -e "${RED}[6/6] Stopping Kafka & PostgreSQL...${NC}"
docker compose -f "$INFRA_DIR/kafka/compose.yml" down
docker compose -f "$INFRA_DIR/postgres/compose.yml" down

echo ""
echo -e "${GREEN}✅ All services stopped.${NC}"
