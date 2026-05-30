package juyeop.jpay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.outbox.OutboxEvent;
import juyeop.jpay.payment.outbox.OutboxEventRepository;
import juyeop.jpay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final UserBalanceTxService userBalanceTxService;
	private final PaymentRepository paymentRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public Optional<Payment> findByExternalId(String externalId) {
		return paymentRepository.findByExternalId(externalId);
	}

	@Transactional
	public Payment deductAndComplete(String externalId, Long userId, Money amount, String merchantId) {
		userBalanceTxService.deduct(userId, amount);
		Payment payment = Payment.completed(externalId, userId, amount, merchantId);
		paymentRepository.save(payment);
		outboxEventRepository.save(buildOutboxEvent(payment));
		return payment;
	}

	@Transactional
	public Payment deductPessimisticAndComplete(String externalId, Long userId, Money amount, String merchantId) {
		userBalanceTxService.deductWithPessimisticLock(userId, amount);
		Payment payment = Payment.completed(externalId, userId, amount, merchantId);
		paymentRepository.save(payment);
		outboxEventRepository.save(buildOutboxEvent(payment));
		return payment;
	}

	@Transactional
	public Payment deductAtomicAndComplete(String externalId, Long userId, Money amount, String merchantId) {
		userBalanceTxService.deductAtomic(userId, amount);
		Payment payment = Payment.completed(externalId, userId, amount, merchantId);
		paymentRepository.save(payment);
		outboxEventRepository.save(buildOutboxEvent(payment));
		return payment;
	}

	@Transactional
	public Payment failPayment(Long paymentId, String reason) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalStateException("Payment not found: " + paymentId));
		payment.fail(reason);
		return payment;
	}

	private OutboxEvent buildOutboxEvent(Payment payment) {
		PaymentCompletedEvent event = new PaymentCompletedEvent(
				payment.getId(), payment.getUserId(), payment.getMerchantId(), payment.getAmount().amount(), Instant.now());
		try {
			return OutboxEvent.create(payment.getId(), PaymentCompletedEvent.TOPIC,
					objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
