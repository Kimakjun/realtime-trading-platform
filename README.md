# Realtime Trading Platform

실시간 트레이딩 플랫폼 데이터 파이프라인 프로젝트입니다. Kafka를 통해 들어오는 데이터를 Flink를 이용해 실시간으로 처리합니다.

---

## 📋 개발환경 (Prerequisites)

| 도구 | 버전 | 비고 |
|------|------|------|
| **Java (JDK)** | 17 | Flink 어플리케이션 빌드 및 실행에 필요 |
| **Docker** | 20.10+ | 인프라 컨테이너 실행 |
| **Docker Compose** | v2+ | `docker compose` (V2) 명령어 사용 |

> [!TIP]
> **Gradle**은 프로젝트에 포함된 Wrapper(`./gradlew`)를 사용하므로 별도 설치가 필요 없습니다.

---

## 🚀 시작하기 (Infrastructure)

모든 명령어는 프로젝트 **Root 디렉토리**에서 실행합니다.

### 전체 실행 / 종료 (Quick Start)
의존성 순서에 맞게 모든 서비스를 한 번에 실행하거나 종료합니다.
```bash
# 전체 실행
bash infra/scripts/start-all.sh

# 전체 종료
bash infra/scripts/stop-all.sh
```

### 시뮬레이터 실행 (Simulator)
인프라(Kafka 등)가 실행된 상태에서, 무작위 데이터가 아닌 실제 트레이딩과 유사한 생애주기(CREATED -> FILLED/CANCELED)를 갖는 가상 주문 데이터를 `order-events.raw` 토픽에 실시간으로 생성합니다.
```bash
# 시뮬레이터 백그라운드 대신 포그라운드로 실행 (종료는 Ctrl+C)
bash infra/scripts/run-simulator.sh
```

### 개별 실행 (수동)

<details>
<summary>서비스를 개별적으로 실행하려면 펼치세요</summary>

#### 1. Kafka 클러스터 실행
Kafka, Kafka-UI, 초기 토픽 생성이 포함됩니다.
```bash
docker compose -f infra/kafka/compose.yml up -d
```

#### 2. ClickHouse 실행
ClickHouse 서버, Web UI(Tabix), 초기 테이블 생성이 포함됩니다.
```bash
docker compose -f infra/clickhouse/compose.yml up -d
```

#### 3. Flink 클러스터 실행
JobManager, TaskManager가 실행됩니다. (Kafka, ClickHouse 네트워크에 연결되므로 먼저 실행되어 있어야 합니다.)
```bash
docker compose -f infra/flink/compose.yml up -d
```

#### 4. Grafana 실행
ClickHouse 데이터소스와 프로비저닝된 대시보드가 포함됩니다. (ClickHouse 네트워크에 연결되므로 먼저 실행되어 있어야 합니다.)
```bash
docker compose -f infra/grafana/compose.yml up -d
```

#### 5. 클러스터 종료
```bash
# Kafka 종료
docker compose -f infra/kafka/compose.yml down

# ClickHouse 종료
docker compose -f infra/clickhouse/compose.yml down

# Flink 종료
docker compose -f infra/flink/compose.yml down

# Grafana 종료
docker compose -f infra/grafana/compose.yml down
```

</details>

---

## 🛠 빌드 및 Job 배포 (Application)

### 1. Flink 어플리케이션 빌드 (Shadow JAR)
```bash
cd flink && ./gradlew clean shadowJar && cd ..
```

### 2. Flink Job 제출 (Submit)
빌드된 JAR 파일을 Flink 클러스터에 업로드하고 실행합니다.
```bash
bash infra/flink/scripts/submit-job.sh
```

---

## 📊 모니터링 (Web UI & Logs)

### Web UI 주소
- **Flink UI:** [http://localhost:8081](http://localhost:8081)
- **Kafka UI:** [http://localhost:8080](http://localhost:8080)
- **ClickHouse UI (Tabix):** [http://localhost:8088](http://localhost:8088)
- **Grafana:** [http://localhost:3001](http://localhost:3001) (기본 계정: admin / admin)

### 로그 확인 (CLI)
- **Flink 처리 결과 보기 (stdout):**
  ```bash
  docker logs -f local-flink-taskmanager
  ```
- **Kafka 초기화 상태 확인:**
  ```bash
  docker logs -f local-kafka-init-topics
  ```

---

## 📂 프로젝트 구조
- `flink/`: Kotlin 기반 Flink 고수준 스트리밍 어플리케이션
- `infra/kafka/`: Kafka 인프라 구성 (Docker Compose)
- `infra/clickhouse/`: ClickHouse 인프라 구성 및 초기 스키마 (Docker Compose)
- `infra/flink/`: Flink 인프라 구성 및 설정 (Docker Compose)
- `infra/grafana/`: Grafana 인프라 구성 및 대시보드 프로비저닝 (Docker Compose)
- `study/`: 학습 정리 및 기술 문서
