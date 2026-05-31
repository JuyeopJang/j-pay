-- ledger_db.accounts 초기 세팅
-- 실행: mysql -h 127.0.0.1 -P 3308 -u jpay -pjpay < load-tests/verify/seed-ledger-accounts.sql
-- OPERATING_CASH (시스템), MERCHANT_RECEIVABLE (merchantId=1), USER_MONEY (userId 1~500)
-- ON DUPLICATE KEY UPDATE는 UNIQUE KEY uk_account_type_owner 기준으로 멱등 적용됨
-- 재실행해도 안전.

USE ledger_db;

INSERT IGNORE INTO accounts (id, account_type, owner_id) VALUES
(1, 'OPERATING_CASH',      0),
(2, 'MERCHANT_RECEIVABLE', 1);

INSERT IGNORE INTO accounts (id, account_type, owner_id)
SELECT
    n + 2       AS id,
    'USER_MONEY' AS account_type,
    n           AS owner_id
FROM (
    WITH RECURSIVE seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 500
    )
    SELECT n FROM seq
) AS numbers;
