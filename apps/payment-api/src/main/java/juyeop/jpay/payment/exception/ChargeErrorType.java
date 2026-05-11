package juyeop.jpay.payment.exception;

import juyeop.jpay.common.web.error.ErrorType;
import org.springframework.http.HttpStatus;

public enum ChargeErrorType implements ErrorType {
	IDEMPOTENCY_CONFLICT("idempotency-conflict", "Idempotency Conflict", HttpStatus.CONFLICT), UPSTREAM_UNAVAILABLE("upstream-unavailable", "PG Unavailable", HttpStatus.SERVICE_UNAVAILABLE);

	private final String slug;
	private final String title;
	private final HttpStatus httpStatus;

	ChargeErrorType(String slug, String title, HttpStatus httpStatus) {
		this.slug = slug;
		this.title = title;
		this.httpStatus = httpStatus;
	}

	@Override
	public String slug() {
		return slug;
	}

	@Override
	public String title() {
		return title;
	}

	@Override
	public HttpStatus httpStatus() {
		return httpStatus;
	}
}
