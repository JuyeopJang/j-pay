package juyeop.jpay.transfer.external.dto;

import java.util.Map;

public sealed interface ExternalBankTransferResult
        permits ExternalBankTransferResult.Succeeded, ExternalBankTransferResult.Failed {

    record Succeeded(String transferRef, Map<String, Object> meta) implements ExternalBankTransferResult {}
    record Failed(String code, String message, Map<String, Object> meta) implements ExternalBankTransferResult {}
}
