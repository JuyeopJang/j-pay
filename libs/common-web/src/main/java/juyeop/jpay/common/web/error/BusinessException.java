package juyeop.jpay.common.web.error;

import lombok.Getter;

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