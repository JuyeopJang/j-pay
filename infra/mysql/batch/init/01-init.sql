CREATE DATABASE IF NOT EXISTS batch_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON batch_db.* TO 'jpay'@'%';
FLUSH PRIVILEGES;

USE batch_db;

-- Spring Batch 메타 테이블은 spring.batch.jdbc.initialize-schema: always 로 자동 생성됨

CREATE TABLE IF NOT EXISTS settlements (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    merchant_id          VARCHAR(64)  NOT NULL,
    period_start         DATE         NOT NULL COMMENT '정산 대상 기간 시작 (inclusive)',
    period_end           DATE         NOT NULL COMMENT '정산 대상 기간 종료 (inclusive)',
    total_amount         BIGINT       NOT NULL,
    payment_count        INT          NOT NULL,
    status               VARCHAR(16)  NOT NULL COMMENT 'PENDING | TRANSFERRED | FAILED',
    transfer_external_id VARCHAR(128)          COMMENT 'transfer-service로 보낸 멱등성 키',
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_merchant_period (merchant_id, period_start, period_end),
    CONSTRAINT chk_settlement_amount CHECK (total_amount > 0),
    CONSTRAINT chk_settlement_status CHECK (status IN ('PENDING', 'TRANSFERRED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS discrepancies (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    reconciliation_date  DATE         NOT NULL COMMENT '대사 기준 날짜',
    external_id          VARCHAR(128)          COMMENT '내부 charge external_id',
    transfer_ref         VARCHAR(64)           COMMENT '은행 이체 참조번호',
    discrepancy_type     VARCHAR(32)  NOT NULL COMMENT 'MISSING_IN_BANK | MISSING_IN_INTERNAL | AMOUNT_MISMATCH',
    internal_amount      BIGINT                COMMENT '내부 기록 금액 (null 이면 내부 레코드 없음)',
    bank_amount          BIGINT                COMMENT '은행 측 금액 (null 이면 은행 레코드 없음)',
    detail               TEXT,
    resolved_at          DATETIME(6)           COMMENT '보정 완료 시각 (null 이면 미처리)',
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_discrepancy_date       (reconciliation_date),
    INDEX idx_discrepancy_unresolved (resolved_at, reconciliation_date),
    CONSTRAINT chk_discrepancy_type CHECK (discrepancy_type IN ('MISSING_IN_BANK', 'MISSING_IN_INTERNAL', 'AMOUNT_MISMATCH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlement_outbox_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    topic       VARCHAR(100) NOT NULL COMMENT 'Kafka 토픽',
    message_key VARCHAR(128) NOT NULL COMMENT 'Kafka 파티셔닝 키',
    payload     TEXT         NOT NULL COMMENT '직렬화된 이벤트 JSON',
    published   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0: 미발행, 1: 발행 완료',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_settlement_outbox_unpublished (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;