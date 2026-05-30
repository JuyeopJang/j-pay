package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.InsufficientFundsException;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.exception.BalanceNotFoundException;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBalanceTxService {
	private final UserBalanceRepository userBalanceRepository;

	@Transactional
	public void deduct(Long userId, Money amount) {
		userBalanceRepository.findByUserId(userId).ifPresentOrElse(balance -> balance.deduct(amount), () -> {
			throw new BalanceNotFoundException(userId);
		});
	}

	@Transactional
	public void deductWithPessimisticLock(Long userId, Money amount) {
		userBalanceRepository.findByUserIdForUpdate(userId).ifPresentOrElse((balance) -> balance.deduct(amount), () -> {
			throw new BalanceNotFoundException(userId);
		});
	}

	@Transactional
	public void deductAtomic(Long userId, Money amount) {
		int updated = userBalanceRepository.deductIfSufficient(userId, amount.amount());
		if (updated == 0) {
			userBalanceRepository.findByUserId(userId)
					.map(b -> { throw new InsufficientFundsException(b.getBalance(), amount); })
					.orElseThrow(() -> new BalanceNotFoundException(userId));
		}
	}

	@Transactional
	public void deposit(Long userId, Money amount) {
		userBalanceRepository.findByUserId(userId).ifPresentOrElse((balance) -> balance.deposit(amount), () -> {
			throw new BalanceNotFoundException(userId);
		});
	}
}
