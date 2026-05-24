package juyeop.jpay.payment.bank.dto;

import java.util.Map;

/**
 * 은행 이체 결과. 성공/실패 두 케이스를 sealed로 분리해
 * 호출자가 두 케이스 모두 처리하도록 컴파일 단에서 강제.
 */
public sealed interface BankTransferResult
        permits BankTransferResult.Succeeded, BankTransferResult.Failed {

    record Succeeded(
            String transferRef,
            Map<String, Object> meta
    ) implements BankTransferResult {
    }

    record Failed(
            String errorCode,
            String message,
            Map<String, Object> meta
    ) implements BankTransferResult {
    }
}