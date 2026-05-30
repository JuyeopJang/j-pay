import { check } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { BASE_URL, MERCHANT_ID, USER_COUNT, CHARGE_AMOUNT, PAYMENT_AMOUNT, IDEMPOTENCY_DUPLICATE_RATE } from './config.js';

// 커스텀 메트릭
export const idempotencyReplayCount  = new Counter('idempotency_replay_count');
export const paymentSuccessCount     = new Counter('payment_success_count');
export const chargeSuccessCount      = new Counter('charge_success_count');
export const insufficientFundsCount  = new Counter('insufficient_funds_count');

// VU 번호(1-based)를 userId로 매핑 (1~USER_COUNT 순환)
export function userId() {
    return (__VU % USER_COUNT) + 1;
}

// UUID v4 생성 (k6 내장 crypto 없을 때 대용)
export function uuid() {
    return `${randomHex(8)}-${randomHex(4)}-4${randomHex(3)}-${randomHex(4)}-${randomHex(12)}`;
}

function randomHex(len) {
    let s = '';
    for (let i = 0; i < len; i++) {
        s += Math.floor(Math.random() * 16).toString(16);
    }
    return s;
}

// 충전 요청 — bankAccountId는 16자 이상 필수
export function charge(uid, idempotencyKey) {
    const bankAccountId = `BA${String(uid).padStart(14, '0')}`;   // 16자 고정
    const res = http.post(
        `${BASE_URL}/charges`,
        JSON.stringify({ amount: CHARGE_AMOUNT, bankAccountId }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey,
                'X-User-Id': String(uid),
            },
        }
    );
    const ok = check(res, { 'charge 2xx': r => r.status === 201 || r.status === 200 });
    if (ok) chargeSuccessCount.add(1);
    return res;
}

// VU별로 직전 반복에서 사용한 실제 결제 키를 추적 — 멱등성 replay에 사용
const _lastPayKey = {};

// 결제 요청
export function pay(uid, idempotencyKey, endpoint) {
    const path = endpoint || '/payments/optimistic';

    // 1% 확률로 직전 반복의 실제 키를 재전송 → payment-api의 idempotency replay 경로 실행
    let key = idempotencyKey;
    const prevKey = _lastPayKey[__VU];
    if (prevKey && Math.random() < IDEMPOTENCY_DUPLICATE_RATE) {
        key = prevKey;
        idempotencyReplayCount.add(1);
    }
    _lastPayKey[__VU] = idempotencyKey;   // 다음 반복을 위해 현재 키 저장

    const res = http.post(
        `${BASE_URL}${path}`,
        JSON.stringify({ amount: PAYMENT_AMOUNT, merchantId: MERCHANT_ID }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': key,
                'X-User-Id': String(uid),
            },
        }
    );
    const ok = check(res, {
        'pay 2xx': r => r.status === 201 || r.status === 200,
        'no 5xx':  r => r.status < 500,
    });
    if (ok) paymentSuccessCount.add(1);
    if (res.status === 409 || (res.body && res.body.includes('INSUFFICIENT'))) {
        insufficientFundsCount.add(1);
    }
    return res;
}
