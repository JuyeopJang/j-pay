/**
 * 시나리오 1 스모크 테스트: E2E 결제 플로우 (2분)
 *
 * 목적: DB 시딩 및 merchantId 픽스 후 ledger-service가 정상 처리하는지 빠르게 확인
 * 프로파일: 50 VU × 2분
 */

import { sleep } from 'k6';
import { userId, charge, pay } from './common/helpers.js';

export const options = {
    scenarios: {
        e2e_flow: {
            executor: 'constant-vus',
            vus: 50,
            duration: '2m',
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const uid = userId();

    const chargeKey = `smoke-charge-${uid}-${__ITER}`;
    charge(uid, chargeKey);

    sleep(0.5);

    const payKey = `smoke-pay-${uid}-${__ITER}`;
    pay(uid, payKey, '/payments/optimistic');

    sleep(0.5);
}
