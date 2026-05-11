-- payment-api의 charges 테이블
-- MySQL 컨테이너 첫 부팅 시 자동 실행됨 (data 볼륨이 비어 있을 때).
-- 변경사항 적용하려면 `docker compose down -v && docker compose up -d`.

USE payment_db;

-- ============================================================================
-- charges: 사용자 충전 시도 (카드 → 머니)
-- 한 charge는 PENDING으로 시작해 PG 응답에 따라 COMPLETED 또는 FAILED 종료
-- ============================================================================
CREATE TABLE charges (
    id                  BIGINT        NOT NULL,
    external_id         VARCHAR(128)  NOT NULL COMMENT '멱등성 키 (Idempotency-Key 헤더)',
    user_id             BIGINT        NOT NULL,
    payment_method_id   VARCHAR(128)  NOT NULL COMMENT 'PG 측 카드 토큰',
    amount              BIGINT        NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    pg_approval_number  VARCHAR(64)            COMMENT '성공 시 PG 승인 번호',
    pg_response_meta    JSON                   COMMENT '응답 메타 (response_code/message/latency 등)',
    failure_reason      VARCHAR(255)           COMMENT '실패 시 사유',
    requested_at        TIMESTAMP(6)  NOT NULL COMMENT '비즈니스 발생 시각',
    completed_at        TIMESTAMP(6)           COMMENT 'PG 응답 확정 시각',
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