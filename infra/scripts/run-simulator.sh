#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$INFRA_DIR")"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}🚀 Starting Order Event Simulator...${NC}"
echo -e "${GREEN}Press [Ctrl+C] to stop generating events.${NC}"
echo ""

cd "$PROJECT_DIR/flink"
./gradlew runSimulator -q --console=plain
