package juyeop.jpay.transfer.external.mock.dto;

import java.time.Instant;

public record ExternalBankMockResponse(
        String transferRef,   // 성공 시 은행 참조번호, 실패 시 null
        String errorCode,     // 실패 시 에러 코드, 성공 시 null
        String message,
        Instant transferredAt,
        Long latencyMs
) {}
