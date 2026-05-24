package juyeop.jpay.transfer.exception;

import juyeop.jpay.common.web.error.ErrorType;
import org.springframework.http.HttpStatus;

public enum TransferErrorType implements ErrorType {

	IDEMPOTENCY_CONFLICT(
			"transfer-idempotency-conflict",
			"Idempotency Conflict",
			HttpStatus.CONFLICT),

	EXTERNAL_BANK_UNAVAILABLE(
			"external-bank-unavailable",
			"External Bank Unavailable",
			HttpStatus.BAD_GATEWAY);

	private final String slug;
	private final String title;
	private final HttpStatus httpStatus;

	TransferErrorType(String slug, String title, HttpStatus httpStatus) {
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
