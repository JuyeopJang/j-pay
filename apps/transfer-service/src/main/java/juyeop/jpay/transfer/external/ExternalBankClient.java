package juyeop.jpay.transfer.external;

import juyeop.jpay.transfer.external.dto.ExternalBankTransferRequest;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferResult;

public interface ExternalBankClient {

    ExternalBankTransferResult transfer(ExternalBankTransferRequest request);
}
