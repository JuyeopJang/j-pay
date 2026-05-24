package juyeop.jpay.payment.bank;

import juyeop.jpay.payment.bank.dto.BankTransferRequest;
import juyeop.jpay.payment.bank.dto.BankTransferResult;

public interface BankTransferClient {

    BankTransferResult transfer(BankTransferRequest request);
}