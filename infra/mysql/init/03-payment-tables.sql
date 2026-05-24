-- payment-api의 charges 테이블
-- MySQL 컨테이너 첫 부팅 시 자동 실행됨 (data 볼륨이 비어 있을 때).
-- 변경사항 적용하려면 `docker compose down -v && docker compose up -d`.

USE payment_db;

-- ============================================================================
-- charges: 사용자 충전 시도 (은행 계좌 → 머니)
-- 한 charge는 PENDING으로 시작해 은행 이체 응답에 따라 COMPLETED 또는 FAILED 종료
-- ============================================================================
CREATE TABLE charges (
    id                  BIGINT        NOT NULL,
    external_id         VARCHAR(128)  NOT NULL COMMENT '멱등성 키 (Idempotency-Key 헤더)',
    user_id             BIGINT        NOT NULL,
    bank_account_id     VARCHAR(128)  NOT NULL COMMENT '등록된 은행 계좌 ID (오픈뱅킹 consent token)',
    amount              BIGINT        NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    transfer_ref        VARCHAR(64)            COMMENT '성공 시 은행 이체 고유 번호',
    bank_response_meta  JSON                   COMMENT '응답 메타 (transferRef/errorCode/latency 등)',
    failure_reason      VARCHAR(255)           COMMENT '실패 시 사유',
    requested_at        TIMESTAMP(6)  NOT NULL COMMENT '비즈니스 발생 시각',
    completed_at        TIMESTAMP(6)           COMMENT '이체 확정 시각',
    created_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_charge_external_id (external_id),
    INDEX idx_charge_user_created (user_id, created_at),
    INDEX idx_charge_status (status),
    CONSTRAINT chk_charge_amount_pos CHECK (amount > 0),
    CONSTRAINT chk_charge_status     CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- payments: 사용자 머니 잔액 차감 결제
-- PENDING → COMPLETED (차감 성공) / PENDING → FAILED (잔액 부족)
-- ============================================================================
CREATE TABLE payments (
    id              BIGINT        NOT NULL,
    external_id     VARCHAR(128)  NOT NULL COMMENT '멱등성 키 (Idempotency-Key 헤더)',
    user_id         BIGINT        NOT NULL,
    merchant_id     VARCHAR(64)   NOT NULL COMMENT '가맹점 식별자',
    amount          BIGINT        NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    failure_reason  VARCHAR(255)           COMMENT '실패 사유',
    requested_at    TIMESTAMP(6)  NOT NULL COMMENT '비즈니스 발생 시각',
    completed_at    TIMESTAMP(6)           COMMENT '확정 시각',
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_external_id (external_id),
    INDEX idx_payment_user_created (user_id, created_at),
    INDEX idx_payment_status (status),
    CONSTRAINT chk_payment_amount_pos CHECK (amount > 0),
    CONSTRAINT chk_payment_status     CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- outbox_events: Transactional Outbox 패턴용 이벤트 발행 대기 테이블
-- payment 저장과 동일 트랜잭션에 INSERT → publisher가 polling 후 Kafka 발행
-- ============================================================================
CREATE TABLE outbox_events (
    id          BIGINT        NOT NULL,
    topic       VARCHAR(100)  NOT NULL COMMENT 'Kafka 토픽명',
    payload     LONGTEXT      NOT NULL COMMENT 'JSON 직렬화된 이벤트 페이로드',
    published   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0=미발행, 1=발행완료',
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- user_balance: 사용자 선불 머니 잔액
-- payment_db에 위치. Week 3 Saga 도입 시 ledger-service로 이전 예정.
-- ============================================================================
CREATE TABLE user_balance (
    id          BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    balance     BIGINT        NOT NULL DEFAULT 0,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_balance_user_id (user_id),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;