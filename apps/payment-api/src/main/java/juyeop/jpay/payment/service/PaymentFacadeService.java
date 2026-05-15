package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.InsufficientFundsException;
import juyeop.jpay.payment.exception.BalanceNotFoundException;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.exception.PaymentErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

	private final PaymentService paymentService;
	private final UserBalanceService userBalanceService;

	public PaymentResponse payOptimistic(String idempotencyKey, Long userId, PaymentRequest request) {
		return paymentService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> process(
						idempotencyKey,
						userId,
						request,
						userBalanceService::deductOptimistic
				));
	}

	public PaymentResponse payPessimistic(String idempotencyKey, Long userId, PaymentRequest request) {
		return paymentService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> process(
						idempotencyKey,
						userId,
						request,
						userBalanceService::deductPessimistic
				));
	}

	public PaymentResponse payWithRedisLock(String idempotencyKey, Long userId, PaymentRequest request) {
		return paymentService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> process(
						idempotencyKey,
						userId,
						request,
						userBalanceService::deductWithRedisLock
				));
	}

	private PaymentResponse replay(Payment payment, Long userId, PaymentRequest request) {
		if (!payment.matches(userId, Money.of(request.amount()), request.merchantId()))
			throw new BusinessException(PaymentErrorType.IDEMPOTENCY_CONFLICT);
		return PaymentResponse.from(payment);
	}

	private PaymentResponse process(
			String idempotencyKey,
			Long userId,
			PaymentRequest request,
			BiConsumer<Long, Money> deductStrategy
	) {
		Payment payment;
		Money amount = Money.of(request.amount());
		try {
			payment = paymentService.createPending(idempotencyKey, userId, amount, request.merchantId());
		} catch (DataIntegrityViolationException e) {
			Payment existing = paymentService.findByExternalId(idempotencyKey)
					.orElseThrow(() -> new IllegalStateException("UNIQUE conflict but row not found"));
			return replay(existing, userId, request);
		}

		try {
			deductStrategy.accept(userId, amount);
			return PaymentResponse.from(paymentService.completePayment(payment.getId()));
		} catch (InsufficientFundsException | BalanceNotFoundException e) {
			payment = paymentService.failPayment(payment.getId(), e.getMessage());
			return PaymentResponse.from(payment);
		}
	}
}