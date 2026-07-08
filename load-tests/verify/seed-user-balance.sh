#!/bin/bash
# user_balance 500만 건 시드 (100,000건씩 50 배치)
# 실행: bash load-tests/verify/seed-user-balance.sh <DB_HOST> <DB_PORT>
# 예시: bash load-tests/verify/seed-user-balance.sh 172.31.1.32 3306

DB_HOST=${1:-127.0.0.1}
DB_PORT=${2:-3307}
DB_USER=jpay
DB_PASS=jpay
DB_NAME=payment_db
BATCH=100000
TOTAL=5000000

echo "Seeding ${TOTAL} users in batches of ${BATCH}..."

for i in $(seq 1 $((TOTAL / BATCH))); do
  START=$(( (i - 1) * BATCH + 1 ))
  END=$(( i * BATCH ))

  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" <<EOF
SET SESSION cte_max_recursion_depth = ${BATCH};
INSERT INTO user_balance (id, user_id, balance, version)
WITH RECURSIVE seq (n) AS (
  SELECT ${START}
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < ${END}
)
SELECT n, n, 1000000, 0 FROM seq
ON DUPLICATE KEY UPDATE balance = 1000000, version = 0;
EOF

  echo "Batch ${i}/50 done (${END} rows)"
done

echo "Seeding complete."
