package juyeop.jpay.payment.dto;

import jakarta.validation.constraints.*;

public record ChargeRequest(
		@Min(1)
		@Max(100_000_000)
		@NotNull
		Long amount,

		@NotBlank
		@Size(min = 16, max = 128)
		String bankAccountId
) {
}