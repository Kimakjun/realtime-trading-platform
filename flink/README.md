# Flink Application & Simulator

이 디렉토리는 Flink 스트리밍 어플리케이션과 유의미한 가상 주문 데이터를 생성하는 **Order Event Simulator**를 포함합니다.

---

## 🎲 Order Event Simulator

기존 `order-events.raw` 토픽으로 단순 무작위 데이터가 아닌, **현실적인 주문 생애주기**를 모사하는 이벤트를 지속적으로 생성하여 전송하는 도구입니다.

### 특징 (Scenario Generator)
하나의 주문이 생성(`CREATED`)되면 메모리에 상태를 유지하다가, 확률에 따라 주문 체결 과정을 진행합니다.
1. `CREATED` (신규 주문 발생)
2. `PARTIALLY_FILLED` (일부 수량 체결)
3. `FILLED` (잔량이 0이 되면 완전 체결)
4. `MODIFIED` (주문 수량 변경)
5. `CANCELED` (주문 취소, 또는 오랫동안 체결되지 않은 주문을 정리)

### 실행 방법

Root 디렉토리 기준으로 제공된 스크립트를 사용하면 쉽습니다:

```bash
# 시뮬레이터 실행 (Ctrl+C 로 종료할 때까지 이벤트를 영구적으로 발생시킴)
bash infra/scripts/run-simulator.sh
```

또는 `flink/` 디렉토리에서 Gradle 태스크를 직접 실행할 수도 있습니다:

```bash
cd flink
./gradlew runSimulator
```

> [!NOTE]
> 시뮬레이터를 실행하기 전에 반드시 Kafka 클러스터가 동작 중이어야 합니다 (`bash infra/scripts/start-all.sh` 등 사용). 
> 시뮬레이터는 기본적으로 `localhost:9092`를 바라보도록 설정되어 있습니다.

---

## ⚡ Flink Job 빌드

스트리밍 어플리케이션(Job) 빌드 패키징(Shadow JAR 생성)은 아래와 같이 수행합니다.

```bash
cd flink
./gradlew clean shadowJar
```
생성된 JAR 파일은 `flink/build/libs/` 내에 위치하게 되며, `infra/flink/scripts/submit-job.sh` 스크립트를 통해 Flink Cluster 로 제출할 수 있습니다.
