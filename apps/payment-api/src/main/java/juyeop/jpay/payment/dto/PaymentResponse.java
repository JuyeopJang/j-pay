package juyeop.jpay.payment.dto;

import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.entity.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        String paymentId,
        PaymentStatus status,
        Long amount,
        String merchantId,
        Instant requestedAt,
        Instant completedAt,
        String failureReason
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                String.valueOf(payment.getId()),
                payment.getStatus(),
                payment.getAmount().amount(),
                payment.getMerchantId(),
                payment.getRequestedAt(),
                payment.getCompletedAt(),
                payment.getFailureReason()
        );
    }
}