/**
 * 시나리오 3: Circuit Breaker 상태 전이 관찰
 *
 * 목적:
 *   - 은행 mock 장애(5xx) 주입 시 CB가 CLOSED → OPEN → HALF_OPEN → CLOSED로
 *     전이되는 것을 실시간으로 검증
 *   - CB OPEN 구간에서 충전 요청이 즉시 실패(fast-fail)되어 스레드를 블록하지 않음을 확인
 *   - 복구 후 HALF_OPEN 탐침 호출(10건)을 통해 자동 재닫힘 확인
 *
 * 아키텍처 (모두 payment-api 8080 내부):
 *   k6 → POST /charges → ChargeFacadeService
 *                             ↓ (in-process)
 *                         BankTransferClientImpl  ← @CircuitBreaker("bankTransfer") + @Retry
 *                             ↓
 *                         BankTransferMockService
 *
 * CB 설정 (payment-api application.yml):
 *   sliding-window-size: 20, minimum-number-of-calls: 5, failure-rate-threshold: 50%
 *   wait-duration-in-open-state: 30s, permitted-number-of-calls-in-half-open-state: 10
 *   Retry: max-attempts 3, 100ms exponential backoff
 *   → Retry(OUTER) → CB(INNER): 각 재시도가 CB 슬라이딩 윈도우에 개별 기록됨
 *
 * 장애 주입 방식 — 매직 넘버 amount 사용:
 *   amount = 99997 → BankTransferMockService 500 응답 → Retry 3회 → CB failure 누적
 *   그 외          → 정상 성공
 *
 * 부하 프로파일: 200 VU × 20분
 *   0~5분   (NORMAL)   : amount=1000  → CB CLOSED
 *   5~10분  (FAULT)    : amount=99997 → CB failure 누적 → OPEN 전환
 *   10~15분 (RECOVERY) : amount=1000  → 30초 후 HALF_OPEN → 10건 성공 → CLOSED
 *   15~20분 (STABLE)   : amount=1000  → CLOSED 안정 확인
 *
 * 관찰 지표:
 *   - 'sc3_charge_error_count' : FAULT 구간 충전 실패 건수 (CB OPEN 시 fast-fail 포함)
 *   - 'sc3_cb_open_count'      : CB OPEN/HALF_OPEN 감지 횟수 (VU 1번 폴링)
 *   - GET /actuator/health/circuitbreakers (payment-api 8080) → state 필드
 *
 * 사후 검증:
 *   SELECT status, COUNT(*) FROM payment_db.charges GROUP BY status;
 */

import { sleep, check } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { userId } from './common/helpers.js';
import { BASE_URL } from './common/config.js';

const chargeErrorCount = new Counter('sc3_charge_error_count');
const cbOpenCount      = new Counter('sc3_cb_open_count');

const FAULT_START_SEC    = 5  * 60;
const RECOVERY_START_SEC = 10 * 60;

// 테스트 시작 시각 — 페이즈 판단 기준
const START_TIME = Date.now();

export const options = {
    scenarios: {
        circuit_breaker: {
            executor: 'constant-vus',
            vus: 200,
            duration: '20m',
        },
    },
    thresholds: {
        // NORMAL/RECOVERY/STABLE 구간 종합 에러율 < 30% (FAULT 구간 포함하면 높아짐)
        'http_req_failed{endpoint:charge}': ['rate<0.30'],
    },
};

export default function () {
    const uid     = userId();
    const elapsed = (Date.now() - START_TIME) / 1000;

    // 페이즈에 따라 amount 결정
    const isFaultPhase = elapsed >= FAULT_START_SEC && elapsed < RECOVERY_START_SEC;
    const amount = isFaultPhase ? 99_997 : 1_000;

    const chargeKey = `sc3-charge-${uid}-${__ITER}`;
    const chargeRes = http.post(
        `${BASE_URL}/charges`,
        JSON.stringify({ amount, bankAccountId: `BA${String(uid).padStart(14, '0')}` }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': chargeKey,
                'X-User-Id': String(uid),
            },
            tags: { endpoint: 'charge' },
        }
    );

    const ok = check(chargeRes, { 'charge 2xx': r => r.status === 201 || r.status === 200 });
    if (!ok) chargeErrorCount.add(1);

    // VU 1번만 CB 상태 폴링 (10초마다)
    if (__VU === 1 && __ITER % 10 === 0) {
        const cbRes = http.get(
            `${BASE_URL}/actuator/health/circuitbreakers`,
            { tags: { endpoint: 'cb_health' } }
        );
        if (cbRes.status === 200) {
            try {
                const body   = JSON.parse(cbRes.body);
                const state  = body?.details?.bankTransfer?.details?.state;
                const phase  = isFaultPhase ? 'FAULT' : elapsed < FAULT_START_SEC ? 'NORMAL' : 'RECOVERY';
                console.log(`[CB] elapsed=${Math.floor(elapsed)}s phase=${phase} state=${state}`);
                if (state === 'OPEN' || state === 'HALF_OPEN') {
                    cbOpenCount.add(1);
                }
            } catch (_) {}
        }
    }

    sleep(1);
}