#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}🛑 Stopping all infrastructure services...${NC}"
echo ""

# Stop in reverse dependency order
echo -e "${RED}[1/4] Stopping Grafana...${NC}"
docker compose -f "$INFRA_DIR/grafana/compose.yml" down

echo -e "${RED}[2/4] Stopping Flink...${NC}"
docker compose -f "$INFRA_DIR/flink/compose.yml" down

echo -e "${RED}[3/4] Stopping ClickHouse...${NC}"
docker compose -f "$INFRA_DIR/clickhouse/compose.yml" down

echo -e "${RED}[4/4] Stopping Kafka...${NC}"
docker compose -f "$INFRA_DIR/kafka/compose.yml" down

echo ""
echo -e "${RED}✅ All services stopped.${NC}"
