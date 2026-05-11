package juyeop.jpay.common.web.error;

import lombok.Getter;

/**
 * 도메인 의미 있는 모든 예외의 base. 도메인별로 이 클래스를 상속해 구체 예외 정의.
 *
 * <pre>
 * throw new BusinessException(ChargeErrorType.CARD_DECLINED, "카드사에서 거절됨");
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorType errorType;

    public BusinessException(ErrorType errorType) {
        this.errorType = errorType;
    }

    public BusinessException(ErrorType errorType, String detail) {
        super(detail);
        this.errorType = errorType;
    }

    public BusinessException(ErrorType errorType, String detail, Throwable cause) {
        super(detail, cause);
        this.errorType = errorType;
    }
}