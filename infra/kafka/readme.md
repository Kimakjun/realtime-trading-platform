### RUN
```
cd realtime-trading-platform

docker compose -f infra/kafka/compose.yml up -d

docker compose -f infra/kafka/compose.yml ps

bash infra/kafka/scripts/smoke-test.sh

### STOP & CLEANUP (명령어 사용 전 경로: realtime-trading-platform)
```bash
docker compose -f infra/kafka/compose.yml down

docker compose -f infra/kafka/compose.yml down -v # 볼륨까지 삭제할때
```