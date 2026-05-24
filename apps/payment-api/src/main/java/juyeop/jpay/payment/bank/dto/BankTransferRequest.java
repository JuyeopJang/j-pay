package juyeop.jpay.payment.bank.dto;

public record BankTransferRequest(
        long amount,
        String transferId,
        String bankAccountId
) {
}