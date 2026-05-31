package juyeop.jpay.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(

        // 송금 받을 대상 userId
        @NotNull
        @Positive
        Long toUserId,

        // 송금 금액 (원 단위, 1 ~ 10,000,000)
        @NotNull
        @Min(1)
        @Max(10_000_000)
        Long amount
) {
}