package juyeop.jpay.common.event;

import java.time.Instant;

public record UserTransferCompletedEvent(
        long transferId,
        long fromUserId,
        long toUserId,
        long amount,
        Instant occurredAt
) implements LedgerEvent {
    public static final String TOPIC = "user-transfer.completed";

    @Override
    public long entityId() { return transferId; }
}