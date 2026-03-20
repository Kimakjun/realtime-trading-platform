#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$INFRA_DIR")"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}🚀 Starting all infrastructure services...${NC}"
echo ""

# ── 1. Kafka ──
echo -e "${GREEN}[1/5] Starting Kafka...${NC}"
docker compose -f "$INFRA_DIR/kafka/compose.yml" up -d

# ── 2. ClickHouse ──
echo -e "${GREEN}[2/5] Starting ClickHouse...${NC}"
docker compose -f "$INFRA_DIR/clickhouse/compose.yml" up -d

echo -e "${YELLOW}  ⏳ Waiting for ClickHouse to be healthy...${NC}"
until docker inspect --format='{{.State.Health.Status}}' local-clickhouse 2>/dev/null | grep -q "healthy"; do
  sleep 2
done
echo -e "${GREEN}  ✅ ClickHouse is healthy${NC}"

# ── 3. Flink cluster ──
echo -e "${GREEN}[3/5] Starting Flink cluster...${NC}"
docker compose -f "$INFRA_DIR/flink/compose.yml" up -d

# ── 4. Grafana ──
echo -e "${GREEN}[4/5] Starting Grafana...${NC}"
docker compose -f "$INFRA_DIR/grafana/compose.yml" up -d

echo ""
echo -e "${GREEN}✅ All infrastructure services started!${NC}"
echo ""
echo "  Kafka UI:           http://localhost:8080"
echo "  ClickHouse UI:      http://localhost:8088"
echo "  Flink UI:           http://localhost:8081"
echo "  Grafana:            http://localhost:3001"

# ── 5. Build & submit Flink job ──
echo ""
echo -e "${YELLOW}🔨 Building & submitting Flink job...${NC}"

echo -e "${GREEN}[5/5] Building Flink application (shadowJar)...${NC}"
(cd "$PROJECT_DIR/flink" && ./gradlew clean shadowJar -q)
echo -e "${GREEN}  ✅ Build complete${NC}"

echo -e "${YELLOW}  ⏳ Waiting for Flink JobManager to be ready...${NC}"
until docker compose -f "$INFRA_DIR/flink/compose.yml" exec -T flink-jobmanager flink list 2>/dev/null; do
  sleep 2
done

JAR_NAME=$(ls "$PROJECT_DIR/flink/build/libs/"*.jar | head -n 1 | xargs basename)
if docker compose -f "$INFRA_DIR/flink/compose.yml" exec -T flink-jobmanager flink run /opt/flink/usrlib/$JAR_NAME; then
  echo -e "${GREEN}  ✅ Flink job submitted${NC}"
else
  echo -e "${RED}  ⚠️  Flink job submission failed. All infrastructure is running — check logs and retry manually:${NC}"
  echo "     bash infra/flink/scripts/submit-job.sh"
fi
