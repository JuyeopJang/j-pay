-- 첫 실행 시 (mysql-data 볼륨이 비어있을 때) 한 번만 실행됨
-- 이후 변경은 볼륨 초기화(`docker compose down -v`) 후 재실행 필요

CREATE DATABASE IF NOT EXISTS payment_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ledger_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS transfer_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS batch_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- jpay 사용자(MYSQL_USER로 자동 생성됨)에게 4개 schema 권한 부여
GRANT ALL PRIVILEGES ON payment_db.*  TO 'jpay'@'%';
GRANT ALL PRIVILEGES ON ledger_db.*   TO 'jpay'@'%';
GRANT ALL PRIVILEGES ON transfer_db.* TO 'jpay'@'%';
GRANT ALL PRIVILEGES ON batch_db.*    TO 'jpay'@'%';
FLUSH PRIVILEGES;