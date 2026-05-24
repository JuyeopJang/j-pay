-- ledger-service의 3개 테이블: accounts / ledger_transactions / ledger_entries
-- MySQL 컨테이너 첫 부팅 시 자동 실행됨 (data 볼륨이 비어 있을 때).
-- 변경사항 적용하려면 `docker compose down -v && docker compose up -d`.

USE ledger_db;

-- ============================================================================
-- accounts: 회계 계정 — 잔액 없이 LedgerEntry 그룹핑 용도
-- 사용자 머니(USER_MONEY) / 가맹점 미정산금(MERCHANT_RECEIVABLE) /
-- 운영 자금(OPERATING_CASH) / 수수료(FEE_REVENUE)
-- ============================================================================
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

-- ============================================================================
-- ledger_transactions: 한 비즈니스 이벤트 (충전/결제/취소/정산/대사보정)
-- ============================================================================
CREATE TABLE ledger_transactions (
    id                  BIGINT        NOT NULL,
    external_id         VARCHAR(128)  NOT NULL COMMENT '멱등성 키 (PaymentId, ChargeId, RECON-yyyymmdd-seq 등)',
    transaction_type    VARCHAR(32)   NOT NULL,
    transaction_status  VARCHAR(16)   NOT NULL,
    total_amount        BIGINT        NOT NULL,
    occurred_at         TIMESTAMP(6)  NOT NULL COMMENT '비즈니스 발생 시각 (created_at과 다를 수 있음)',
    created_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_tx_external_id (external_id),
    INDEX idx_lt_occurred (occurred_at),
    CONSTRAINT chk_lt_total_amount_pos CHECK (total_amount > 0),
    CONSTRAINT chk_lt_type             CHECK (transaction_type IN (
        'CHARGE', 'PAYMENT', 'REFUND', 'SETTLEMENT', 'RECON_ADJUST'
    )),
    CONSTRAINT chk_lt_status           CHECK (transaction_status IN ('POSTED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ledger_entries: 거래의 차/대변 한 줄 (immutable, append-only)
-- ============================================================================
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