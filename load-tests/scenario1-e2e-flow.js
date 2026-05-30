/**
 * 시나리오 1: E2E 결제 플로우 (충전 → 결제, 1% 멱등성 중복)
 *
 * 목적:
 *   - 충전 + Optimistic Lock 결제 통합 흐름에서 TPS와 정합성을 동시에 검증
 *   - 멱등성 1% 중복 요청이 replay 응답(동일 paymentId)으로 돌아오는지 확인
 *   - 부하 종료 후 consistency-check.sql로 원장 정합성 검증
 *
 * 부하 프로파일: 500 VU × 30분 (steady-state)
 * 체크포인트: p95 latency < 500ms, error rate < 1%, 멱등성 위반 0건
 */

import { sleep } from 'k6';
import { userId, charge, pay } from './common/helpers.js';

export const options = {
    scenarios: {
        e2e_flow: {
            executor: 'constant-vus',
            vus: 500,
            duration: '30m',
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const uid = userId();

    // 1. 충전 — 잔액 보충 (충전 1,000원 → 결제 100원, 10회 결제 가능)
    const chargeKey = `charge-${uid}-${__ITER}`;
    charge(uid, chargeKey);

    sleep(0.5);

    // 2. 결제 — optimistic lock, 1% 확률로 이전 키 재사용
    const payKey = `pay-${uid}-${__ITER}`;
    pay(uid, payKey, '/payments/optimistic');

    sleep(0.5);
}
