package juyeop.jpay.common.event;

import java.time.Instant;

public record TransferFailedEvent(
        String externalId,
        String merchantId,
        long amount,
        String failureReason,
        Instant failedAt
) {
    public static final String TOPIC = "transfer.failed";
}
