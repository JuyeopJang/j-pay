package juyeop.jpay.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(

        @NotNull
        @Min(1)
        @Max(100_000_000)
        Long amount,

        @NotBlank
        String merchantId
) {
}