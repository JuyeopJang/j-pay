-- ledger-service 정합성 검증 — mysql-ledger (3308) 에 연결해서 실행
-- 실행: mysql -h 127.0.0.1 -P 3308 -u jpay -pjpay < load-tests/verify/consistency-check-ledger.sql
-- 모든 결과가 0 이어야 통과

-- =============================================================================
-- [2a] 원장 PAYMENT POSTED 건수 (payment CHECK_2a count와 값이 같아야 함)
-- =============================================================================
SELECT
    'CHECK_2a: ledger_payment_count' AS check_name,
    COUNT(*)                         AS count
FROM ledger_db.ledger_transactions
WHERE transaction_type = 'PAYMENT' AND transaction_status = 'POSTED';

-- =============================================================================
-- [2b] 원장 CHARGE POSTED 건수 (payment CHECK_2b count와 값이 같아야 함)
-- =============================================================================
SELECT
    'CHECK_2b: ledger_charge_count' AS check_name,
    COUNT(*)                        AS count
FROM ledger_db.ledger_transactions
WHERE transaction_type = 'CHARGE' AND transaction_status = 'POSTED';

-- =============================================================================
-- [3] 원장 차변 합계 ≠ 대변 합계 (복식부기 위반)
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
-- [5a] 원장 PAYMENT DEBIT 합계 (payment CHECK_5a total과 값이 같아야 함)
-- =============================================================================
SELECT
    'CHECK_5a: ledger_payment_debit_total' AS check_name,
    COALESCE(SUM(le.amount), 0)            AS total
FROM ledger_db.ledger_transactions lt
JOIN ledger_db.ledger_entries le ON lt.id = le.transaction_id
WHERE lt.transaction_type = 'PAYMENT' AND lt.transaction_status = 'POSTED' AND le.side = 'DEBIT';

-- =============================================================================
-- [5b] 원장 CHARGE CREDIT 합계 (payment CHECK_5b total과 값이 같아야 함)
-- =============================================================================
SELECT
    'CHECK_5b: ledger_charge_credit_total' AS check_name,
    COALESCE(SUM(le.amount), 0)            AS total
FROM ledger_db.ledger_transactions lt
JOIN ledger_db.ledger_entries le ON lt.id = le.transaction_id
WHERE lt.transaction_type = 'CHARGE' AND lt.transaction_status = 'POSTED' AND le.side = 'CREDIT';