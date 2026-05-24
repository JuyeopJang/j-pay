USE transfer_db;

-- 가맹점 정산 송금 내역
-- 정산 배치(batch-app)가 POST /transfers 로 송금 요청을 보내면 이 테이블에 기록된다.
-- external_id: 배치가 발급한 멱등성 키 (동일 요청 재시도 안전)
CREATE TABLE IF NOT EXISTS transfers (
    id             BIGINT          NOT NULL,
    external_id    VARCHAR(100)    NOT NULL,
    merchant_id    VARCHAR(50)     NOT NULL,
    bank_account_id VARCHAR(50)    NOT NULL,
    amount         BIGINT          NOT NULL,   -- 단위: 원(KRW)
    status         VARCHAR(20)     NOT NULL,   -- PENDING | COMPLETED | FAILED
    transfer_ref   VARCHAR(100),               -- 은행이 발급한 이체 참조번호
    failure_reason VARCHAR(500),
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_transfers              PRIMARY KEY (id),
    CONSTRAINT uq_transfers_external_id  UNIQUE      (external_id),
    CONSTRAINT chk_transfer_amount       CHECK       (amount > 0),
    CONSTRAINT chk_transfer_status       CHECK       (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
