package juyeop.jpay.transfer.external.mock.dto;

public record ExternalBankMockRequest(
        Long transferId,
        String bankAccountId,
        long amount
) {}
