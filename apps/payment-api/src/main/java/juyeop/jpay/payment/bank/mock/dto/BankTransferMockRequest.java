package juyeop.jpay.payment.bank.mock.dto;

public record BankTransferMockRequest(
        long amount,
        String transferId,
        String bankAccountId
) {
}