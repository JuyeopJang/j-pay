package juyeop.jpay.common.event;

import java.time.Instant;

public record PaymentCompletedEvent(
        long paymentId,
        long userId,
        String merchantId,
        long amount,       // Money.amount (KRW, 소수점 없음)
        Instant occurredAt
) {
    public static final String TOPIC = "payment.completed";
}