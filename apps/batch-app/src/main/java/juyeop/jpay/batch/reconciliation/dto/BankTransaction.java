package juyeop.jpay.batch.reconciliation.dto;

import java.time.Instant;

public record BankTransaction(
        String transferRef,
        String externalId,
        long amount,
        Instant processedAt
) {}
