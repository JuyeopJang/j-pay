/**
 * 락 전략 TPS 비교 — 시나리오 1과 동일한 부하(500 VU)로 4가지 전략 순차 실행
 *
 * 목적:
 *   - 같은 조건에서 Optimistic Lock / Pessimistic Lock / Redis Distributed Lock / Atomic Update TPS 비교
 *   - 상대 비율(A vs B)을 이력서 수치로 사용 — 절대값이 아닌 비율이 의미 있음
 *   - 각 전략의 지연 분포(p50/p95/p99) 차이로 트레이드오프 근거 확보
 *
 * 실행 방법: 전략별로 개별 실행 후 결과 비교
 *   k6 run --env STRATEGY=optimistic  lock-comparison.js
 *   k6 run --env STRATEGY=pessimistic lock-comparison.js
 *   k6 run --env STRATEGY=redis-lock  lock-comparison.js
 *   k6 run --env STRATEGY=atomic      lock-comparison.js
 *
 * 부하 프로파일: 500 VU × 10분 (steady-state, 결과 비교 충분한 구간)
 */

import { sleep } from 'k6';
import { userId, charge, pay } from './common/helpers.js';

const STRATEGY = __ENV.STRATEGY || 'optimistic';
// 재실행 시 이전 키와 충돌하지 않도록 실행 시각을 키에 포함
const RUN_ID = Date.now();
const ENDPOINT_MAP = {
    optimistic:  '/payments/optimistic',
    pessimistic: '/payments/pessimistic',
    'redis-lock': '/payments/redis-lock',
    atomic:      '/payments/atomic',
};
const endpoint = ENDPOINT_MAP[STRATEGY];
if (!endpoint) throw new Error(`Unknown STRATEGY: ${STRATEGY}`);

export const options = {
    scenarios: {
        lock_comparison: {
            executor: 'constant-vus',
            vus: 500,
            duration: '10m',
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
};

export default function () {
    const uid = userId();

    // 잔액 보충 후 결제 — 시나리오 1과 동일 패턴
    // RUN_ID 포함으로 3회 연속 실행 시 키 충돌(idempotency replay 오염) 방지
    charge(uid, `lc-charge-${RUN_ID}-${__VU}-${__ITER}`);
    sleep(0.3);
    pay(uid, `lc-pay-${RUN_ID}-${__VU}-${__ITER}`, endpoint);
    sleep(0.3);
}
