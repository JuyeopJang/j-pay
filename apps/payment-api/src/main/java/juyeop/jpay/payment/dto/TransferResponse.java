package juyeop.jpay.payment.dto;

public record TransferResponse(
        Long fromUserId,
        Long toUserId,
        Long amount,
        Long fromBalanceAfter
) {
}