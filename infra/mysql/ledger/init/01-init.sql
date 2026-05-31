CREATE DATABASE IF NOT EXISTS ledger_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON ledger_db.* TO 'jpay'@'%';
FLUSH PRIVILEGES;

USE ledger_db;

CREATE TABLE accounts (
    id              BIGINT        NOT NULL COMMENT 'snowflake id',
    account_type    VARCHAR(32)   NOT NULL COMMENT 'USER_MONEY / MERCHANT_RECEIVABLE / OPERATING_CASH / FEE_REVENUE',
    owner_id        BIGINT        NOT NULL COMMENT 'user_id 또는 merchant_id (시스템 계정은 0)',
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_type_owner (account_type, owner_id),
    INDEX idx_account_owner (owner_id),
    CONSTRAINT chk_account_type CHECK (account_type IN (
        'USER_MONEY', 'MERCHANT_RECEIVABLE', 'OPERATING_CASH', 'FEE_REVENUE'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_transactions (
    id                  BIGINT        NOT NULL,
    external_id         VARCHAR(128)  NOT NULL COMMENT '멱등성 키',
    transaction_type    VARCHAR(32)   NOT NULL,
    transaction_status  VARCHAR(16)   NOT NULL,
    total_amount        BIGINT        NOT NULL,
    occurred_at         TIMESTAMP(6)  NOT NULL COMMENT '비즈니스 발생 시각',
    created_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_tx_external_id (external_id),
    INDEX idx_lt_occurred (occurred_at),
    CONSTRAINT chk_lt_total_amount_pos CHECK (total_amount > 0),
    CONSTRAINT chk_lt_type             CHECK (transaction_type IN (
        'CHARGE', 'PAYMENT', 'TRANSFER', 'REFUND', 'SETTLEMENT', 'RECON_ADJUST'
    )),
    CONSTRAINT chk_lt_status           CHECK (transaction_status IN ('POSTED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_entries (
    id              BIGINT        NOT NULL,
    transaction_id  BIGINT        NOT NULL,
    account_id      BIGINT        NOT NULL,
    side            VARCHAR(8)    NOT NULL,
    amount          BIGINT        NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_le_transaction       (transaction_id),
    INDEX idx_le_account_created   (account_id, created_at),
    CONSTRAINT fk_le_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT fk_le_account     FOREIGN KEY (account_id)     REFERENCES accounts(id),
    CONSTRAINT chk_le_amount_pos CHECK (amount > 0),
    CONSTRAINT chk_le_side       CHECK (side IN ('DEBIT', 'CREDIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;