package juyeop.jpay.common.event;

import java.time.Instant;

public record ChargeCompletedEvent(
        long chargeId,
        long userId,
        long amount,
        Instant occurredAt
) implements LedgerEvent {
    public static final String TOPIC = "charge.completed";

    @Override
    public long entityId() { return chargeId; }
}