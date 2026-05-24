package juyeop.jpay.payment.bank;

public class BankTransferException extends RuntimeException {

    public BankTransferException(String message) {
        super(message);
    }

    public BankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}