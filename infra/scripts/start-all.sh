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
echo -e "${GREEN}[1/6] Starting Kafka...${NC}"
docker compose -f "$INFRA_DIR/kafka/compose.yml" up -d

# ── 2. PostgreSQL ──
echo -e "${GREEN}[2/6] Starting PostgreSQL...${NC}"
docker compose -f "$INFRA_DIR/postgres/compose.yml" up -d

echo -e "${YELLOW}  ⏳ Waiting for PostgreSQL to be healthy...${NC}"
until docker inspect --format='{{.State.Health.Status}}' local-postgres 2>/dev/null | grep -q "healthy"; do
  sleep 2
done
echo -e "${GREEN}  ✅ PostgreSQL is healthy${NC}"

# ── 3. ClickHouse ──
echo -e "${GREEN}[3/6] Starting ClickHouse...${NC}"
docker compose -f "$INFRA_DIR/clickhouse/compose.yml" up -d

echo -e "${YELLOW}  ⏳ Waiting for ClickHouse to be healthy...${NC}"
until docker inspect --format='{{.State.Health.Status}}' local-clickhouse 2>/dev/null | grep -q "healthy"; do
  sleep 2
done
echo -e "${GREEN}  ✅ ClickHouse is healthy${NC}"

# ── 4. Flink cluster ──
echo -e "${GREEN}[4/6] Starting Flink cluster...${NC}"
docker compose -f "$INFRA_DIR/flink/compose.yml" up -d

# ── 5. Grafana ──
echo -e "${GREEN}[5/6] Starting Grafana...${NC}"
docker compose -f "$INFRA_DIR/grafana/compose.yml" up -d

echo ""
echo -e "${GREEN}✅ All infrastructure services started!${NC}"
echo ""
echo "  Kafka UI:           http://localhost:8080"
echo "  ClickHouse UI:      http://localhost:8088"
echo "  PostgreSQL:         localhost:5432 (trading_user / trading_password)"
echo "  Flink UI:           http://localhost:8081"
echo "  Grafana:            http://localhost:3001"

# ── 6. Build & submit Flink job ──
echo ""
echo -e "${YELLOW}🔨 Building & submitting Flink job...${NC}"

echo -e "${GREEN}[6/6] Building Flink application (shadowJar)...${NC}"
(cd "$PROJECT_DIR/flink" && ./gradlew clean shadowJar -q)
echo -e "${GREEN}  ✅ Build complete${NC}"

echo -e "${YELLOW}  ⏳ Waiting for Flink JobManager to be ready...${NC}"
until docker compose -f "$INFRA_DIR/flink/compose.yml" exec -T flink-jobmanager flink list 2>/dev/null; do
  sleep 2
done

JAR_NAME=$(ls "$PROJECT_DIR/flink/build/libs/"*.jar | head -n 1 | xargs basename)
if docker compose -f "$INFRA_DIR/flink/compose.yml" exec -T flink-jobmanager flink run -d /opt/flink/usrlib/$JAR_NAME; then
  echo -e "${GREEN}  ✅ Flink job submitted${NC}"
else
  echo -e "${RED}  ⚠️  Flink job submission failed. All infrastructure is running — check logs and retry manually:${NC}"
  echo "     bash infra/flink/scripts/submit-job.sh"
fi

# ── 7. API Gateway (Spring Boot) ──
echo ""
echo -e "${GREEN}[7/8] Starting API Gateway (Spring Boot)...${NC}"
nohup bash -c "cd '$PROJECT_DIR/api-gateway' && ./gradlew bootRun -q" > /tmp/api-gateway.log 2>&1 &
API_GW_PID=$!
echo $API_GW_PID > /tmp/api-gateway.pid
echo -e "${GREEN}  ✅ API Gateway started (PID: $API_GW_PID, log: /tmp/api-gateway.log)${NC}"

# ── 8. Frontend (React Vite) ──
echo -e "${GREEN}[8/8] Starting Frontend dev server...${NC}"
nohup bash -c "cd '$PROJECT_DIR/frontend' && npm run dev" > /tmp/frontend.log 2>&1 &
FE_PID=$!
echo $FE_PID > /tmp/frontend.pid
echo -e "${GREEN}  ✅ Frontend started (PID: $FE_PID, log: /tmp/frontend.log)${NC}"

echo ""
echo -e "${GREEN}✅ All services started!${NC}"
echo ""
echo "  📊 Infra"
echo "    Kafka UI:           http://localhost:8080"
echo "    ClickHouse UI:      http://localhost:8088"
echo "    PostgreSQL:         localhost:5432"
echo "    Flink UI:           http://localhost:8081"
echo "    Grafana:            http://localhost:3001"
echo ""
echo "  🖥️  Application"
echo "    API Gateway:        http://localhost:8082"
echo "    Frontend:           http://localhost:5173"
