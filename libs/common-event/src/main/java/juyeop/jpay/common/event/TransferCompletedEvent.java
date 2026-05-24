package juyeop.jpay.common.event;

import java.time.Instant;

public record TransferCompletedEvent(
        String externalId,
        String merchantId,
        long amount,
        String transferRef,
        Instant completedAt
) {
    public static final String TOPIC = "transfer.completed";
}
