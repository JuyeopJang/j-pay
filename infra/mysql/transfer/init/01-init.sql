CREATE DATABASE IF NOT EXISTS transfer_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON transfer_db.* TO 'jpay'@'%';
FLUSH PRIVILEGES;

USE transfer_db;

CREATE TABLE IF NOT EXISTS transfers (
    id              BIGINT          NOT NULL,
    external_id     VARCHAR(100)    NOT NULL,
    merchant_id     VARCHAR(50)     NOT NULL,
    bank_account_id VARCHAR(50)     NOT NULL,
    amount          BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    transfer_ref    VARCHAR(100),
    failure_reason  VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_transfers             PRIMARY KEY (id),
    CONSTRAINT uq_transfers_external_id UNIQUE      (external_id),
    CONSTRAINT chk_transfer_amount      CHECK       (amount > 0),
    CONSTRAINT chk_transfer_status      CHECK       (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;