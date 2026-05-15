package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.exception.PaymentErrorType;
import juyeop.jpay.payment.repository.DistributedLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserBalanceService {
	private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);

	private final UserBalanceTxService userBalanceTxService;
	private final DistributedLockRepository distributedLockRepository;

	@Retryable(
			retryFor = {ObjectOptimisticLockingFailureException.class},
			maxAttempts = 5,
			backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 1.5, random = true)
	)
	public void deductOptimistic(Long userId, Money amount) {
		userBalanceTxService.deduct(userId, amount);
	}

	@Recover
	public void recoverOptimistic(RuntimeException e, Long userId, Money amount) {
		if (e instanceof ObjectOptimisticLockingFailureException) {
			throw new BusinessException(PaymentErrorType.OPTIMISTIC_LOCK_FAILURE);
		}
		throw e;
	}

	public void deductPessimistic(Long userId, Money amount) {
		userBalanceTxService.deductWithPessimisticLock(userId, amount);
	}

	public void deductWithRedisLock(Long userId, Money amount) {
		String lockKey = "lock:user:%s:balance".formatted(userId);

		if (!distributedLockRepository.tryAcquire(lockKey, LOCK_WAIT_TIMEOUT, LOCK_TTL)) {
			throw new BusinessException(PaymentErrorType.DISTRIBUTED_LOCK_ACQUISITION_FAILED);
		}

		try {
			userBalanceTxService.deduct(userId, amount);
		} finally {
			distributedLockRepository.release(lockKey);
		}
	}
}
