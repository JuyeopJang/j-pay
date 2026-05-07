package juyeop.jpay.common.core;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Money current, Money requested) {
        super("Insufficient funds: current=" + current + ", requested=" + requested);
    }
}