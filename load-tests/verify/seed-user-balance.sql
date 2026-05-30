-- 부하 테스트용 user_balance 500건 초기 데이터
-- 실행: mysql -u jpay -pjpay payment_db < seed-user-balance.sql
-- user_id 1~500, 초기 잔액 1,000,000원 (충전 없이도 결제 10,000회 가능)

USE payment_db;

INSERT INTO user_balance (id, user_id, balance, version)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 500
)
SELECT
    n,          -- id (1~500, snowflake 아님 — 테스트 전용)
    n,          -- user_id
    1000000,    -- 초기 잔액 1,000,000원
    0           -- version
FROM seq
ON DUPLICATE KEY UPDATE balance = 1000000, version = 0;
