package juyeop.jpay.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.exception.PaymentErrorType;
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
				.orElseThrow(() -> new BusinessException(PaymentErrorType.PAYMENT_NOT_FOUND));
		payment.fail(reason);
		return payment;
	}

	private OutboxEvent buildOutboxEvent(Payment payment) {
		return OutboxEvent.create(payment.getId(), PaymentCompletedEvent.TOPIC,
				new PaymentCompletedEvent(payment.getId(), payment.getUserId(), payment.getMerchantId(),
						payment.getAmount().amount(), Instant.now()),
				objectMapper);
	}
}
