package juyeop.jpay.payment.exception;

public class BalanceNotFoundException extends RuntimeException {

    public BalanceNotFoundException(Long userId) {
        super("Balance not found for userId=" + userId);
    }
}