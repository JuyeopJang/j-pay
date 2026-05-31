package juyeop.jpay.common.event;

import java.time.Instant;

public interface LedgerEvent {
    long entityId();
    long amount();
    Instant occurredAt();
}