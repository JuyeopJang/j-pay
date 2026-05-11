package juyeop.jpay.common.web.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 모든 예외를 RFC 7807 ProblemDetail로 변환하는 공통 핸들러.
 * common-web에 의존하는 모든 app(payment-api, ledger-service 등)에 자동 적용.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 도메인 의미 있는 예외 — ErrorType 그대로 매핑. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorType type = ex.getErrorType();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(type.httpStatus(), ex.getMessage());
        pd.setType(URI.create(type.typeUri()));
        pd.setTitle(type.title());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    /** @Valid 검증 실패 — 다중 필드 에러를 errors 배열에 담음. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "reason", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .toList();

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body validation failed");
        pd.setType(URI.create(CommonErrorType.INVALID_REQUEST.typeUri()));
        pd.setTitle(CommonErrorType.INVALID_REQUEST.title());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("errors", errors);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    /** 필수 헤더 누락 (예: Idempotency-Key). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Missing header: " + ex.getHeaderName());
        pd.setType(URI.create(CommonErrorType.MISSING_HEADER.typeUri()));
        pd.setTitle(CommonErrorType.MISSING_HEADER.title());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("missingHeader", ex.getHeaderName());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    /** 명시적으로 처리되지 않은 예외 — 500 + 로그. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create(CommonErrorType.INTERNAL_ERROR.typeUri()));
        pd.setTitle(CommonErrorType.INTERNAL_ERROR.title());
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}