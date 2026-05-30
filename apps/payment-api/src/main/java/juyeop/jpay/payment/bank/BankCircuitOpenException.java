package juyeop.jpay.payment.bank;

public class BankCircuitOpenException extends BankTransferException {

    public BankCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
