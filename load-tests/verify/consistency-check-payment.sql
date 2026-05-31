-- payment-api 정합성 검증 — mysql-payment (3307) 에 연결해서 실행
-- 실행: mysql -h 127.0.0.1 -P 3307 -u jpay -pjpay < load-tests/verify/consistency-check-payment.sql
-- 모든 결과가 0 이어야 통과

-- =============================================================================
-- [1] 잔액 음수 발생 여부 — balance < 0 이면 동시성 제어 실패
-- =============================================================================
SELECT
    'CHECK_1: balance_negative' AS check_name,
    COUNT(*)                    AS violation_count
FROM payment_db.user_balance
WHERE balance < 0;

-- =============================================================================
-- [2a] 결제 COMPLETED 건수 (ledger CHECK_2a와 비교용)
-- =============================================================================
SELECT
    'CHECK_2a: payment_completed_count' AS check_name,
    COUNT(*)                            AS count
FROM payment_db.payments
WHERE status = 'COMPLETED';

-- =============================================================================
-- [2b] 충전 COMPLETED 건수 (ledger CHECK_2b와 비교용)
-- =============================================================================
SELECT
    'CHECK_2b: charge_completed_count' AS check_name,
    COUNT(*)                           AS count
FROM payment_db.charges
WHERE status = 'COMPLETED';

-- =============================================================================
-- [4] 미발행 Outbox 이벤트 잔존 여부 — 0이 아니면 Outbox 폴러 지연 or 누락
--     부하 테스트 종료 후 30초 이상 대기한 뒤 실행해야 폴러 처리 시간 확보
-- =============================================================================
SELECT
    'CHECK_4: outbox_unpublished' AS check_name,
    COUNT(*)                      AS violation_count
FROM payment_db.outbox_events
WHERE published = 0;

-- =============================================================================
-- [5a] 결제 금액 합계 (ledger CHECK_5a와 비교용)
-- =============================================================================
SELECT
    'CHECK_5a: payment_total_amount' AS check_name,
    COALESCE(SUM(amount), 0)         AS total
FROM payment_db.payments
WHERE status = 'COMPLETED';

-- =============================================================================
-- [5b] 충전 금액 합계 (ledger CHECK_5b와 비교용)
-- =============================================================================
SELECT
    'CHECK_5b: charge_total_amount' AS check_name,
    COALESCE(SUM(amount), 0)        AS total
FROM payment_db.charges
WHERE status = 'COMPLETED';