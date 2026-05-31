package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.InsufficientFundsException;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.Payment;
import juyeop.jpay.payment.exception.BalanceNotFoundException;
import juyeop.jpay.payment.exception.PaymentErrorType;
import juyeop.jpay.payment.repository.DistributedLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

	private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final PaymentService paymentService;
	private final DistributedLockRepository distributedLockRepository;

	@Retryable(
			retryFor = ObjectOptimisticLockingFailureException.class,
			maxAttempts = 5,
			backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 1.5, random = true)
	)
	public PaymentResponse payOptimistic(String idempotencyKey, Long userId, PaymentRequest request) {
		return pay(idempotencyKey, userId, request, paymentService::deductAndComplete);
	}

	// Spring Retry는 non-retryable 예외도 recover로 라우팅한다. BusinessException은 그대로 재전파.
	@Recover
	public PaymentResponse recoverOptimistic(Exception e, String idempotencyKey, Long userId, PaymentRequest request) {
		if (e instanceof BusinessException be) throw be;
		throw new BusinessException(PaymentErrorType.OPTIMISTIC_LOCK_FAILURE);
	}

	public PaymentResponse payPessimistic(String idempotencyKey, Long userId, PaymentRequest request) {
		return pay(idempotencyKey, userId, request, paymentService::deductPessimisticAndComplete);
	}

	public PaymentResponse payAtomic(String idempotencyKey, Long userId, PaymentRequest request) {
		return pay(idempotencyKey, userId, request, paymentService::deductAtomicAndComplete);
	}

	public PaymentResponse payWithRedisLock(String idempotencyKey, Long userId, PaymentRequest request) {
		return paymentService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> {
					String lockKey = "lock:user:%s:balance".formatted(userId);
					if (!distributedLockRepository.tryAcquire(lockKey, LOCK_WAIT_TIMEOUT, LOCK_TTL)) {
						throw new BusinessException(PaymentErrorType.DISTRIBUTED_LOCK_ACQUISITION_FAILED);
					}
					try {
						return process(idempotencyKey, userId, request, paymentService::deductAndComplete);
					} finally {
						distributedLockRepository.release(lockKey);
					}
				});
	}

	private PaymentResponse pay(String idempotencyKey, Long userId, PaymentRequest request,
								PaymentExecutionStrategy strategy) {
		return paymentService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> process(idempotencyKey, userId, request, strategy));
	}

	private PaymentResponse replay(Payment payment, Long userId, PaymentRequest request) {
		if (!payment.matches(userId, Money.of(request.amount()), request.merchantId()))
			throw new BusinessException(PaymentErrorType.IDEMPOTENCY_CONFLICT);
		return PaymentResponse.from(payment);
	}

	private PaymentResponse process(String idempotencyKey, Long userId, PaymentRequest request,
									PaymentExecutionStrategy strategy) {
		try {
			return PaymentResponse.from(
					strategy.execute(idempotencyKey, userId, Money.of(request.amount()), request.merchantId()));
		} catch (DataIntegrityViolationException e) {
			Payment existing = paymentService.findByExternalId(idempotencyKey)
					.orElseThrow(() -> new IllegalStateException("UNIQUE conflict but row not found"));
			return replay(existing, userId, request);
		} catch (InsufficientFundsException e) {
			throw new BusinessException(PaymentErrorType.INSUFFICIENT_BALANCE);
		} catch (BalanceNotFoundException e) {
			throw new BusinessException(PaymentErrorType.BALANCE_NOT_FOUND);
		}
	}
}
