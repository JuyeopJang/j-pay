package juyeop.jpay.payment.exception;

import juyeop.jpay.common.web.error.ErrorType;
import org.springframework.http.HttpStatus;

public enum PaymentErrorType implements ErrorType {

	IDEMPOTENCY_CONFLICT(
			"payment-idempotency-conflict",
			"Idempotency Conflict",
			HttpStatus.CONFLICT),

	INSUFFICIENT_BALANCE(
			"insufficient-balance",
			"Insufficient Balance",
			HttpStatus.UNPROCESSABLE_ENTITY),

	BALANCE_NOT_FOUND(
			"balance-not-found",
			"User Balance Not Found",
			HttpStatus.NOT_FOUND),

	OPTIMISTIC_LOCK_FAILURE(
			"optimistic-lock-failure",
			"Concurrent Modification Conflict",
			HttpStatus.CONFLICT),

	DISTRIBUTED_LOCK_ACQUISITION_FAILED(
			"distributed-lock-acquisition-failure",
			"Concurrent Modification Conflict",
			HttpStatus.CONFLICT
	);

	private final String slug;
	private final String title;
	private final HttpStatus httpStatus;

	PaymentErrorType(String slug, String title, HttpStatus httpStatus) {
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