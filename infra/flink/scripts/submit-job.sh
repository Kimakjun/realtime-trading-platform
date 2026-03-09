#!/bin/bash
set -e

JAR_NAME=$(ls flink/build/libs/*.jar | head -n 1 | xargs basename)

chmod +x infra/flink/scripts/submit-job.sh

docker compose \
  -f infra/flink/compose.yml \
  exec flink-jobmanager \
  flink run /opt/flink/usrlib/$JAR_NAME