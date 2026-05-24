package juyeop.jpay.payment.bank.mock.dto;

import java.time.Instant;

public record BankTransactionRecord(
        String transferRef,
        String externalId,
        long amount,
        Instant processedAt
) {}
