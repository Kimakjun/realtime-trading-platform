#!/bin/bash
set -e

echo "[init-topics] waiting for kafka (already healthy)..."
sleep 2

echo "[init-topics] creating topics..."

kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic raw_order_events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic executed_orders \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic market-ticks.raw \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic dlq.order-events \
  --partitions 1 \
  --replication-factor 1

echo "[init-topics] done."
kafka-topics --bootstrap-server kafka:29092 --list