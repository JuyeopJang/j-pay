export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// VU pool을 USER_COUNT 명의 사용자로 분산: VU index → userId 1~N
export const USER_COUNT = 500;

export const MERCHANT_ID = '1';

// 충전 단위: 1,000원
export const CHARGE_AMOUNT = 1_000;
// 결제 단위: 100원 (충전 1회에 결제 10회 가능)
export const PAYMENT_AMOUNT = 100;

// 멱등성 중복 요청 비율: 1%
export const IDEMPOTENCY_DUPLICATE_RATE = 0.01;
