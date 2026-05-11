package juyeop.jpay.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인 무관 공통 ErrorType. 각 app은 자기 도메인 ErrorType을 별도로 정의.
 */
public enum CommonErrorType implements ErrorType {

    INVALID_REQUEST("invalid-request",   "Invalid Request",   HttpStatus.BAD_REQUEST),
    MISSING_HEADER ("missing-header",    "Missing Header",    HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED("unauthenticated",   "Unauthenticated",   HttpStatus.UNAUTHORIZED),
    FORBIDDEN      ("forbidden",         "Forbidden",         HttpStatus.FORBIDDEN),
    NOT_FOUND      ("not-found",         "Not Found",         HttpStatus.NOT_FOUND),
    INTERNAL_ERROR ("internal-error",    "Internal Error",    HttpStatus.INTERNAL_SERVER_ERROR);

    private final String slug;
    private final String title;
    private final HttpStatus httpStatus;

    CommonErrorType(String slug, String title, HttpStatus httpStatus) {
        this.slug = slug;
        this.title = title;
        this.httpStatus = httpStatus;
    }

    @Override public String slug()           { return slug; }
    @Override public String title()          { return title; }
    @Override public HttpStatus httpStatus() { return httpStatus; }
}