package juyeop.jpay.payment.bank.mock.dto;

import java.time.Instant;

public record BankTransferMockResponse(
        String transferRef,
        String errorCode,
        String message,
        Instant transferredAt,
        Long latencyMs
) {
}