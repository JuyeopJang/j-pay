# 부하 테스트 실행 가이드

## 목적

모놀리식 병목 실측 → MSA 전환 후 목표 달성을 데이터로 증명한다.

- **모놀리식**: 단일 앱, 동기 원장, 스레드풀 소진으로 TPS 상한 발생
- **MSA**: payment-api 수평 확장, 원장 비동기(Kafka)로 TPS 목표 달성

부하 생성기: **NGrinder** (EC2 c5.xlarge)
대상 서버: **AWS EC2** (ap-northeast-2)

---

## Phase 1 — 모놀리식 부하 테스트 ✅ 완료

### 최종 결과 (5차, 현실적 시나리오)

| 지표 | 값 |
|---|---|
| TPS | **757.8** (Peak 1,000) |
| Mean Test Time | 250ms |
| Tomcat busy threads | 포화 (App CPU 77.6%) |
| HikariCP active | 안정 (Infra CPU 58.2%) |
| Error rate | **0%** |
| Executed Tests | 671,549건 |
| 누적 정합성 검증 | imbalance = 0 |

**테스트 시나리오**: 충전 30% / 결제 60% / 이체 10%, 랜덤 userId (5M 풀)

**병목 결론**: bank mock `Thread.sleep(200~500ms random)`으로 충전 스레드 지속 점유. 결제·이체는 빠르나 전체 앱을 함께 Scale-out해야 하는 모놀리식 구조의 한계 확인. 단일 인스턴스 TPS 상한 ~760.

### 참고 — 시나리오 개선 과정

| 차수 | TPS | Error | 주요 변경 |
|---|---|---|---|
| 4차 | 633.3 | 4.3% | random userId, 충전+결제 교번, bank mock 200ms 고정 |
| 5차 | **757.8** | **0%** | 역할 분리(30/60/10), 랜덤 userId 5M, bank mock 200~500ms random, deposit 비관락 |

---

### EC2 구성

| 인스턴스 ID | 타입 | 역할 |
|---|---|---|
| i-0751634ea06f47983 | c5.xlarge | Infra (MySQL:3306, Redis:6379) |
| i-0388d423a421fc16f | c5.xlarge | App (payment-api:8080) |
| i-008436887ef8e132b | c5.xlarge | NGrinder (Controller:8300 + Agent) |

Key Pair: `~/.ssh/jpay-load-test.pem`
Security Group: Infra(sg-01ef0fec942c1ecf6), App(sg-0ca47b19f25c993b5), NGrinder(sg-0406654475503634e)
> App SG에 NGrinder SG → 8080 인바운드 허용 추가됨

### 재시작 절차

#### 1단계: EC2 3대 시작

```sh
aws ec2 start-instances --instance-ids i-0751634ea06f47983 i-0388d423a421fc16f i-008436887ef8e132b
aws ec2 wait instance-running --instance-ids i-0751634ea06f47983 i-0388d423a421fc16f i-008436887ef8e132b

# IP 확인
aws ec2 describe-instances --instance-ids i-0751634ea06f47983 \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text  # Infra

aws ec2 describe-instances --instance-ids i-0388d423a421fc16f \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text  # App

aws ec2 describe-instances --instance-ids i-008436887ef8e132b \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text  # NGrinder
```

> Private IP는 고정: Infra=172.31.1.32, App=172.31.10.127

#### 2단계: Infra EC2 — MySQL + Redis 기동

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<INFRA_IP>
sudo docker compose ps   # 내려가 있으면:
sudo docker compose up -d
```

#### 3단계: App EC2 — payment-api 기동

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<APP_IP>

nohup java -jar ~/payment-api.jar \
  --spring.datasource.url='jdbc:mysql://172.31.1.32:3306/payment_db?characterEncoding=UTF-8&serverTimezone=Asia/Seoul' \
  --spring.datasource.username=jpay \
  --spring.datasource.password=jpay \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
  --spring.data.redis.host=172.31.1.32 \
  --spring.data.redis.port=6379 \
  --app.snowflake.node-id=1 \
  > ~/app.log 2>&1 &

curl http://172.31.10.127:8080/actuator/health
```

#### 4단계: NGrinder — Validate

NGrinder UI: `http://<NGRINDER_IP>:8300` (admin / admin)

- Admin → Agent Management → agent Approved 확인
- Script → ChargeTest → Validate 실행
- 통과 기준: charges 201, payments/pessimistic 201, Errors 0

#### 5단계: 부하 테스트 실행

NGrinder UI → Performance Test → Create Test (또는 이전 테스트 Clone)

| 항목 | 값 |
|---|---|
| Script | ChargeTest |
| Vuser per Agent | 198 |
| Duration | 15분 |
| Ramp-Up | Enable / Initial 10 / Interval 30s / Step 10 |

메트릭 수집 (로컬 터미널에서 동시 실행):

```sh
bash /tmp/collect-metrics.sh
```

#### 6단계: 정합성 검증

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<INFRA_IP>

sudo docker exec jpay-mysql mysql -u jpay -pjpay payment_db -e "
SELECT COUNT(*) AS balance_negative FROM user_balance WHERE balance < 0;
SELECT COUNT(*) AS charge_count FROM charges WHERE status = 'COMPLETED';
SELECT COUNT(*) AS payment_count FROM payments WHERE status = 'COMPLETED';
SELECT
  SUM(CASE WHEN side = 'DEBIT'  THEN amount ELSE 0 END) AS total_debit,
  SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END) AS total_credit,
  SUM(CASE WHEN side = 'DEBIT'  THEN amount ELSE 0 END)
  - SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END) AS imbalance
FROM ledger_entries;
"
```

통과 조건: `balance_negative` = 0, `imbalance` = 0

#### 7단계: EC2 중지

```sh
aws ec2 stop-instances --instance-ids i-0751634ea06f47983 i-0388d423a421fc16f i-008436887ef8e132b
```

---

## Phase 2 — MSA 부하 테스트

> 모놀리식 결과 기록 완료. payment-api 2대 수평 확장, 원장 비동기(Kafka) 처리.

### 목표

| 지표 | 목표 |
|---|---|
| TPS | 1000 이상 |
| Error rate | < 1% |
| 정합성 | imbalance = 0 |

### 최종 결과 ✅ 완료


| 지표 | 값 |
|---|---|
| TPS | **1,007.1** (Peak 1,327) |
| Mean Test Time | 215ms |
| Executed Tests | 884,505건 |
| Error rate | **0%** |
| 정합성 | imbalance = 0 |
| App #1 CPU peak | 76% |
| App #2 CPU peak | 63% |
| Infra CPU | 25% |

**테스트 시나리오**: 충전 30% / 결제 60% / 이체 10%, 랜덤 userId (5M 풀), VUser 228 (6 process × 38 threads)

**정합성 검증 결과** (Kafka lag 소진 후):

| Check | payment_db | ledger_db | 결과 |
|---|---|---|---|
| balance_negative | 0 | — | ✅ |
| payment 건수 | 634,365 | 634,365 | ✅ |
| charge 건수 | 120,722 | 120,722 | ✅ |
| 복식부기 위반 | — | 0 | ✅ |
| outbox_unpublished | 0 | — | ✅ |
| payment 금액 | 63,436,500 | 63,436,500 | ✅ |
| charge 금액 | 120,722,000 | 120,722,000 | ✅ |

> Kafka lag 소진까지 약 35분 소요 (payment.completed 단일 파티션 + 단일 컨슈머 스레드 제약)

### EC2 구성

| 인스턴스 ID | 타입 | 역할 |
|---|---|---|
| i-0751634ea06f47983 | c5.2xlarge | Infra (MySQL:3306, Redis:6379, Kafka:9092) |
| i-0d61d140e3a4199b7 | c5.xlarge | App #1 (payment-api:8080, node-id=1) |
| i-0388d423a421fc16f | c5.xlarge | App #2 (payment-api:8080, node-id=2) |
| (ledger EC2) | c5.xlarge | Ledger (ledger-service:8081, node-id=3) |
| i-008436887ef8e132b | c5.large | NGrinder (Controller:8300 + Agent) |

ALB: `jpay-payment-alb-1923595921.ap-northeast-2.elb.amazonaws.com` → App #1, #2 라운드로빈

> NGrinder EC2 /etc/hosts 및 agent `extra_hosts`에 ALB private IP(172.31.15.40) 매핑 필요 (VPC internal)

### 스크립트 변경 사항 (모놀리식 대비)

| 항목 | 모놀리식 | MSA |
|---|---|---|
| TARGET | 172.31.10.127:8080 | ALB private IP (172.31.24.126) |
| Vuser | 198 | 228 (6×38, 1000 TPS 목표) |
| THREADS_PER_PROCESS | 33 | 38 |
| USER_COUNT | 5,000,000 | 5,000,000 |
| 시나리오 | 충전 30% / 결제 60% / 이체 10% | 동일 |

### 재시작 절차

#### 1단계: EC2 기동

```sh
aws ec2 start-instances --instance-ids i-0751634ea06f47983 i-0d61d140e3a4199b7 i-0388d423a421fc16f i-008436887ef8e132b
aws ec2 wait instance-running --instance-ids i-0751634ea06f47983 i-0d61d140e3a4199b7 i-0388d423a421fc16f i-008436887ef8e132b
```

> Private IP 고정: Infra=172.31.1.32

#### 2단계: Infra EC2 — 컨테이너 기동

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<INFRA_IP>
docker compose ps   # 내려가 있으면:
docker compose up -d
```

MySQL, Redis, Kafka 모두 healthy 확인.

#### 3단계: App EC2 — payment-api 기동

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<APP1_IP>
bash ~/start-payment-1.sh

ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<APP2_IP>
bash ~/start-payment-2.sh

# Ledger EC2
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<LEDGER_IP>
bash ~/start-ledger.sh
```

#### 4단계: 데이터 시딩

```sh
# Infra EC2에서 실행 (이미 완료된 경우 생략)
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<INFRA_IP>
docker exec jpay-mysql mysql -ujpay -pjpay payment_db -e "SELECT COUNT(*) FROM user_balance;"  # 5000000 확인
docker exec jpay-mysql mysql -ujpay -pjpay ledger_db -e "SELECT COUNT(*) FROM accounts;"       # 5000002 확인

# 미시딩 시 로컬에서 실행
bash load-tests/verify/seed-user-balance.sh <INFRA_IP> 3306
```

#### 5단계: NGrinder Validate

NGrinder UI: `http://<NGRINDER_IP>:8300` (admin / admin)

- Script → ChargeTest → Validate 실행
- 통과 기준: /charges 201, /payments/pessimistic 201, Errors 0

#### 6단계: 부하 테스트 실행

NGrinder UI → Performance Test → Create Test

| 항목 | 값 |
|---|---|
| Script | ChargeTest |
| Vuser per Agent | 198 |
| Duration | 15분 |
| Ramp-Up | Enable / Initial 10 / Interval 30s / Step 10 |

#### 7단계: 정합성 검증

```sh
ssh -i ~/.ssh/jpay-load-test.pem ec2-user@<INFRA_IP>

# payment_db
docker exec jpay-mysql mysql -ujpay -pjpay payment_db -e "
SELECT COUNT(*) AS balance_negative FROM user_balance WHERE balance < 0;
SELECT COUNT(*) AS charge_completed  FROM charges  WHERE status = 'COMPLETED';
SELECT COUNT(*) AS payment_completed FROM payments WHERE status = 'COMPLETED';
SELECT COUNT(*) AS outbox_unpublished FROM outbox_events WHERE published = 0;
"

# ledger_db
docker exec jpay-mysql mysql -ujpay -pjpay ledger_db -e "
SELECT
  SUM(CASE WHEN side = 'DEBIT'  THEN amount ELSE 0 END) AS total_debit,
  SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END) AS total_credit,
  SUM(CASE WHEN side = 'DEBIT'  THEN amount ELSE 0 END)
  - SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END) AS imbalance
FROM ledger_entries;
"
```

통과 조건: `balance_negative` = 0, `outbox_unpublished` = 0, `imbalance` = 0

#### 8단계: EC2 중지

```sh
aws ec2 stop-instances --instance-ids i-0751634ea06f47983 i-0d61d140e3a4199b7 i-0388d423a421fc16f i-008436887ef8e132b
```

---

## 재시작 시 체크리스트

- [ ] EC2 5대 start + IP 확인
- [ ] Infra EC2: `docker compose ps` — MySQL, Redis, Kafka healthy
- [ ] App #1, #2: payment-api 기동 + `/actuator/health` UP
- [ ] Ledger EC2: ledger-service 기동 + `/actuator/health` UP
- [ ] NGrinder UI: agent Approved 확인
- [ ] user_balance 500만 건 확인 (`SELECT COUNT(*) FROM user_balance`)
- [ ] ledger accounts 500만 건 확인 (`SELECT COUNT(*) FROM accounts`)
- [ ] NGrinder Validate 통과 (충전 201, 결제 201)