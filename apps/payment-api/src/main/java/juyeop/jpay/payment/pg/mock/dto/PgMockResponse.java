package juyeop.jpay.payment.pg.mock.dto;

import java.time.Instant;

public record PgMockResponse(
        String approvalNumber,
        String errorCode,
        String message,
        Instant approvedAt,
        Long latencyMs
) {
}