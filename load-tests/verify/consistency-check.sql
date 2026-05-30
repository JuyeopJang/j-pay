-- 부하 테스트 종료 후 데이터 정합성 검증 스크립트
-- 실행: mysql -u root -p -e "source consistency-check.sql" (또는 각 쿼리 개별 실행)
-- 모든 쿼리 결과가 0 이어야 정합성 통과

-- =============================================================================
-- [1] 잔액 음수 발생 여부 — balance < 0 이면 동시성 제어 실패
-- =============================================================================
SELECT
    'CHECK_1: balance_negative' AS check_name,
    COUNT(*)                    AS violation_count
FROM payment_db.user_balance
WHERE balance < 0;

-- =============================================================================
-- [2a] 결제 건수 = 원장(ledger) PAYMENT 트랜잭션 건수
--      원장이 누락되면 이중 원장 정합성 위반
-- =============================================================================
SELECT
    'CHECK_2a: payment_ledger_mismatch' AS check_name,
    ABS(
        (SELECT COUNT(*) FROM payment_db.payments WHERE status = 'COMPLETED')
        -
        (SELECT COUNT(*) FROM ledger_db.ledger_transactions WHERE transaction_type = 'PAYMENT' AND transaction_status = 'POSTED')
    ) AS violation_count;

-- =============================================================================
-- [2b] 충전 건수 = 원장(ledger) CHARGE 트랜잭션 건수
-- =============================================================================
SELECT
    'CHECK_2b: charge_ledger_mismatch' AS check_name,
    ABS(
        (SELECT COUNT(*) FROM payment_db.charges WHERE status = 'COMPLETED')
        -
        (SELECT COUNT(*) FROM ledger_db.ledger_transactions WHERE transaction_type = 'CHARGE' AND transaction_status = 'POSTED')
    ) AS violation_count;

-- =============================================================================
-- [3] 원장 차변 합계 ≠ 대변 합계 (복식부기 위반)
--     각 ledger_transaction 내에서 DEBIT 합 = CREDIT 합 이어야 함
-- =============================================================================
SELECT
    'CHECK_3: double_entry_imbalance' AS check_name,
    COUNT(*)                          AS violation_count
FROM (
    SELECT
        transaction_id,
        SUM(CASE WHEN side = 'DEBIT'  THEN amount ELSE 0 END) AS debit_sum,
        SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END) AS credit_sum
    FROM ledger_db.ledger_entries
    GROUP BY transaction_id
    HAVING debit_sum <> credit_sum
) imbalanced;

-- =============================================================================
-- [4] 미발행 Outbox 이벤트 잔존 여부 — 0이 아니면 Outbox 폴러 지연 or 누락
--     부하 테스트 종료 후 30초 이상 대기한 뒤 실행해야 폴러 처리 시간 확보
-- =============================================================================
-- =============================================================================
-- [5a] 결제 금액 합계 정합성 — payments.amount 합 = 원장 PAYMENT DEBIT 합
--      건수는 맞아도 금액이 다를 경우 감지
-- =============================================================================
SELECT
    'CHECK_5a: payment_amount_mismatch' AS check_name,
    ABS(
        (SELECT COALESCE(SUM(amount), 0) FROM payment_db.payments WHERE status = 'COMPLETED')
        -
        (SELECT COALESCE(SUM(le.amount), 0)
         FROM ledger_db.ledger_transactions lt
         JOIN ledger_db.ledger_entries le ON lt.id = le.transaction_id
         WHERE lt.transaction_type = 'PAYMENT' AND lt.transaction_status = 'POSTED' AND le.side = 'DEBIT')
    ) AS violation_count;

-- =============================================================================
-- [5b] 충전 금액 합계 정합성 — charges.amount 합 = 원장 CHARGE CREDIT 합
-- =============================================================================
SELECT
    'CHECK_5b: charge_amount_mismatch' AS check_name,
    ABS(
        (SELECT COALESCE(SUM(amount), 0) FROM payment_db.charges WHERE status = 'COMPLETED')
        -
        (SELECT COALESCE(SUM(le.amount), 0)
         FROM ledger_db.ledger_transactions lt
         JOIN ledger_db.ledger_entries le ON lt.id = le.transaction_id
         WHERE lt.transaction_type = 'CHARGE' AND lt.transaction_status = 'POSTED' AND le.side = 'CREDIT')
    ) AS violation_count;

-- =============================================================================
SELECT
    'CHECK_4: outbox_unpublished' AS check_name,
    COUNT(*)                      AS violation_count
FROM payment_db.outbox_events
WHERE published = 0;
