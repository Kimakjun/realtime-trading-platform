# Realtime Trading Platform

본 레포지토리는 고가용성 및 고성능을 목표로 설계된 **실시간 멀티-티어 주식 트레이딩 플랫폼**입니다. Spring Boot, Apache Flink, Kafka, ClickHouse, React(Jotai + Ant Design)를 활용하여 MSA(Microservices Architecture) 형태의 이벤트 기반 엔터프라이즈 모의 거래소를 구현하였습니다.

👉 **[시스템 아키텍처 및 데이터 흐름도 상세 진단 보기 (docs/04-architecture.md)](docs/04-architecture.md)**

---

## 🚀 시작하기 (Getting Started)

모든 명령어는 루트(Root) 디렉토리 기준입니다.

### 1. 단축 명령어 (Quick Start)
인프라 및 서비스를 일괄 구동하거나 종료합니다.
```bash
# 전체 실행 (Kafka, DB, Flink 클러스터 등 시작)
bash infra/scripts/start-all.sh

# 전체 종료
bash infra/scripts/stop-all.sh
```

### 2. 개별 서비스 실행 가이드
필요 시 서비스별로 분산 실행할 수 있습니다.
- **Kafka 클러스터**: `docker compose -f infra/kafka/compose.yml up -d`
- **ClickHouse**: `docker compose -f infra/clickhouse/compose.yml up -d`
- **Flink 클러스터**: `docker compose -f infra/flink/compose.yml up -d`
- **Grafana**: `docker compose -f infra/grafana/compose.yml up -d`

*(로컬 웹 데몬 실행: `cd frontend && npm run dev`, `cd api-gateway && ./gradlew bootRun`)*

### 3. Flink 어플리케이션 배포
Flink 매칭 엔진을 빌드하여 클러스터에 배포(Submit)해야 실시간 거래가 매칭됩니다.
```bash
# Flink JAR 빌드
cd flink && ./gradlew clean shadowJar && cd ..
# Flink JobManager 제출
bash infra/flink/scripts/submit-job.sh
```

---

## 🔗 포트 할당 및 모니터링 (Ports)

- **React Frontend**: [http://localhost:5173](http://localhost:5173)
- **Spring API Gateway**: [http://localhost:8082](http://localhost:8082)
- **Flink Dashboard**: [http://localhost:8081](http://localhost:8081)
- **Kafka UI**: [http://localhost:8080](http://localhost:8080)
- **Grafana**: [http://localhost:3001](http://localhost:3001) (계정: `admin` / `admin`)
- **ClickHouse Tabix UI**: [http://localhost:8088](http://localhost:8088)
