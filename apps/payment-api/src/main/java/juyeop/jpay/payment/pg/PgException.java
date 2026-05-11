package juyeop.jpay.payment.pg;

/**
 * PG 통신 시스템 실패 — 타임아웃, 5xx, 네트워크 오류 등.
 * 비즈니스 거절(카드 한도 초과 등)은 예외가 아닌 PgAuthorizeResult.Declined로 표현.
 */
public class PgException extends RuntimeException {

    public PgException(String message) {
        super(message);
    }

    public PgException(String message, Throwable cause) {
        super(message, cause);
    }
}