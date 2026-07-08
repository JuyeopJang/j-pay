-- 부하 테스트용 user_balance 500만 건 초기 데이터
-- EC2에서 직접 실행: bash load-tests/verify/seed-user-balance.sh
-- user_id 1~5,000,000, 초기 잔액 1,000,000원
-- 주의: 5M 행 삽입으로 약 10~20분 소요

USE payment_db;

SET SESSION cte_max_recursion_depth = 100000;

-- 50 배치 × 100,000건 = 5,000,000건