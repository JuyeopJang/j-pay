# 부하 테스트 실행 가이드

## 사전 요구사항

- **k6** v0.46 이상 (`brew install k6`)
- **Docker Desktop** 실행 중
- **JDK 21** (`java -version`으로 확인)

---

## 1단계: 인프라 기동

```sh
# 프로젝트 루트에서 실행
docker compose down -v   # 이전 데이터 완전 초기화 (최초 실행 또는 재시작 시)
docker compose up -d
```

컨테이너 상태 확인:

```sh
docker compose ps
```

MySQL이 `healthy` 상태일 때까지 대기 (보통 15~30초).

---

## 2단계: 초기 데이터 세팅

### 잔액 데이터 (payment_db)

userId 1~500, 잔액 1,000,000원으로 초기화한다.

```sh
mysql -h 127.0.0.1 -P 3306 -u jpay -pjpay < load-tests/verify/seed-user-balance.sql
```

재실행 시 `ON DUPLICATE KEY UPDATE`로 잔액이 1,000,000원으로 리셋된다.

### 원장 계정 데이터 (ledger_db)

ledger-service가 이벤트를 처리하려면 accounts 레코드가 사전에 존재해야 한다.

```sh
mysql -h 127.0.0.1 -P 3306 -u jpay -pjpay < load-tests/verify/seed-ledger-accounts.sql
```

- `OPERATING_CASH` (owner_id=0): 충전 시 운영 자금 계정
- `MERCHANT_RECEIVABLE` (owner_id=1): 결제 수취 가맹점 계정 (k6 merchantId=1)
- `USER_MONEY` (owner_id=1~500): 사용자 500명 계정

재실행해도 `INSERT IGNORE`로 안전하다.

---

## 3단계: 애플리케이션 기동

터미널 3개를 열어 각각 실행한다.

```sh
# 터미널 1
./gradlew :apps:payment-api:bootRun

# 터미널 2
./gradlew :apps:ledger-service:bootRun

# 터미널 3
./gradlew :apps:transfer-service:bootRun

# 터미널 4
./gradlew :apps:batch-app:bootRun
```

| 서비스 | 포트 | 헬스체크 |
|---|---|---|
| payment-api | 8080 | `curl localhost:8080/actuator/health` |
| ledger-service | 8081 | `curl localhost:8081/actuator/health` |
| transfer-service | 8082 | `curl localhost:8082/actuator/health` |
| batch-app | 8083 | `curl localhost:8083/actuator/health` |

3개 모두 `"status":"UP"` 확인 후 다음 단계로 넘어간다.

---

## 4단계: Grafana 대시보드 확인

브라우저에서 `http://localhost:3000` 접속 (admin / admin).

좌측 메뉴 → Dashboards → **j-pay 부하 테스트** 대시보드 열기.

> 서비스가 떠 있으면 JVM Heap, HikariCP, MySQL 패널에 바로 데이터가 표시된다.
> k6 관련 패널(TPS, 지연, 에러율)은 k6 실행 후 채워진다.

---

## 5단계: 부하 테스트 실행

모든 k6 명령은 **프로젝트 루트**에서 실행한다.
`--out` 플래그로 k6 메트릭을 Prometheus로 전송해 Grafana에 표시한다.

### 스모크 테스트 — E2E 플로우 사전 검증 (50 VU × 2분)

본격적인 시나리오 실행 전 DB 시딩과 서비스 연동이 정상인지 확인한다.

```sh
k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  load-tests/scenario1-smoke.js
```

ledger-service 로그에 `Record in retry and not yet recovered`가 없고, Grafana consumer lag가 0에 수렴하면 정상. 이후 정합성 검증(6단계)으로 4개 항목 모두 `0` 확인 후 시나리오 1로 진행한다.

---

### 시나리오 1 — E2E 결제 플로우 (500 VU × 30분)

```sh
k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  load-tests/scenario1-e2e-flow.js
```

- 충전 → 결제 흐름, 멱등성 1% 중복 포함
- 완료 후 바로 정합성 검증(6단계) 실행

### 시나리오 2 — Circuit Breaker 장애 주입 (200 VU × 20분)

```sh
k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  load-tests/scenario3-circuit-breaker.js
```

- 0~5분 (NORMAL): 정상 운영 → CB CLOSED
- 5~10분 (FAULT): `amount=99997`로 은행 mock 500 응답 → CB 누적 실패 → OPEN
- 10~15분 (RECOVERY): 정상 amount 복귀 → 30초 후 HALF_OPEN → 10건 성공 → CLOSED
- 15~20분 (STABLE): CB CLOSED 안정 확인
- Grafana **Circuit Breaker 상태** 패널에서 전이 확인

### 락 전략 TPS 비교 (500 VU × 10분 × 4회)

4회 연속 실행. DB 초기화 없이 `RUN_ID`로 키가 분리되므로 바로 이어서 실행해도 된다.

```sh
k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  --env STRATEGY=optimistic \
  load-tests/lock-comparison.js

k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  --env STRATEGY=pessimistic \
  load-tests/lock-comparison.js

k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  --env STRATEGY=redis-lock \
  load-tests/lock-comparison.js

k6 run \
  --out experimental-prometheus-rw=http://localhost:9090/api/v1/write \
  --env STRATEGY=atomic \
  load-tests/lock-comparison.js
```

각 실행이 끝나면 k6 summary에서 `http_req_duration{p(95)}` 수치를 기록해 비교한다.

---

## 6단계: 정합성 검증

시나리오 1 종료 후 **30초 이상 대기** (Outbox 폴러 처리 시간 확보) 후 실행.

```sh
mysql -h 127.0.0.1 -P 3306 -u jpay -pjpay < load-tests/verify/consistency-check.sql
```

**4개 쿼리 모두 `violation_count = 0`이어야 통과.**

| 검증 항목 | 의미 |
|---|---|
| CHECK_1: balance_negative | 잔액 음수 → 동시성 제어 실패 |
| CHECK_2: payment_ledger_mismatch | 결제 수 ≠ 원장 수 → 이중 원장 누락 |
| CHECK_3: double_entry_imbalance | 차변 합 ≠ 대변 합 → 복식부기 위반 |
| CHECK_4: outbox_unpublished | 미발행 Outbox 잔존 → 이벤트 유실 |

---

## 재실행 시 초기화 절차

시나리오를 처음부터 다시 실행하려면:

```sh
# 1. DB 볼륨 포함 전체 초기화
docker compose down -v
docker compose up -d

# 2. 데이터 재세팅 (MySQL healthy 확인 후)
mysql -h 127.0.0.1 -P 3306 -u jpay -pjpay < load-tests/verify/seed-user-balance.sql
mysql -h 127.0.0.1 -P 3306 -u jpay -pjpay < load-tests/verify/seed-ledger-accounts.sql
```

> 시나리오 1은 동일 키(`charge-${uid}-${__ITER}`)로 재실행하면 모든 요청이 idempotency replay로 처리된다. 반드시 DB를 초기화해야 한다.
> 락 비교 스크립트는 `RUN_ID = Date.now()`로 키가 분리되므로 DB 초기화 없이 재실행 가능.
