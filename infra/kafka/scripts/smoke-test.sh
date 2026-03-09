#!/bin/bash
set -e

echo "== list topics =="
docker compose -f infra/kafka/compose.yml exec kafka \
  kafka-topics --bootstrap-server kafka:9092 --list

echo "== produce one message =="
docker compose -f infra/kafka/compose.yml exec -T kafka \
  kafka-console-producer --bootstrap-server kafka:9092 --topic order-events.raw <<'EOF'
{"eventId":"e1","orderId":"o1","type":"CREATED","symbol":"005930","qty":10,"price":70000}
EOF

echo "== consume one message =="
docker compose -f infra/kafka/compose.yml exec kafka \
  kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic order-events.raw \
  --from-beginning \
  --max-messages 1