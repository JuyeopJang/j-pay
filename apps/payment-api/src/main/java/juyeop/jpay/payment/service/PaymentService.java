package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Optional<Payment> findByExternalId(String externalId) {
        return paymentRepository.findByExternalId(externalId);
    }

    @Transactional
    public Payment createPending(String externalId, Long userId, Money amount, String merchantId) {
        Payment payment = Payment.pending(externalId, userId, amount, merchantId);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment completePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));
        payment.complete();
        return payment;
    }

    @Transactional
    public Payment failPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));
        payment.fail(reason);
        return payment;
    }
}