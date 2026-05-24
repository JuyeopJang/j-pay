package juyeop.jpay.transfer.external.dto;

public record ExternalBankTransferRequest(
        Long transferId,
        String bankAccountId,
        long amount
) {}
