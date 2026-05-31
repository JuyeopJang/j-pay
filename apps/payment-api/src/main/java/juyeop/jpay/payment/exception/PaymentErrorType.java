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
			HttpStatus.CONFLICT),

	TRANSFER_TO_SELF(
			"transfer-to-self",
			"Cannot Transfer To Self",
			HttpStatus.BAD_REQUEST),

	RECEIVER_NOT_FOUND(
			"receiver-not-found",
			"Receiver Balance Not Found",
			HttpStatus.NOT_FOUND),

	CHARGE_NOT_FOUND(
			"charge-not-found",
			"Charge Not Found",
			HttpStatus.NOT_FOUND),

	PAYMENT_NOT_FOUND(
			"payment-not-found",
			"Payment Not Found",
			HttpStatus.NOT_FOUND);

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