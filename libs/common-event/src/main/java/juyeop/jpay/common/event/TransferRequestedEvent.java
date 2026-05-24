package juyeop.jpay.common.event;

import java.time.Instant;

public record TransferRequestedEvent(
        String externalId,      // 멱등성 키 (settlement Job이 발급: "SETTLE-{merchantId}-{date}")
        String merchantId,
        String bankAccountId,
        long amount,
        Instant requestedAt
) {
    public static final String TOPIC = "transfer.requested";
}
