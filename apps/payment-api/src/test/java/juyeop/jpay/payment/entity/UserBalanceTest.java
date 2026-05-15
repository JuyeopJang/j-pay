package juyeop.jpay.payment.entity;

import juyeop.jpay.common.core.InsufficientFundsException;
import juyeop.jpay.common.core.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserBalanceTest {
	private static final Money AMOUNT = Money.of(10_000L);
	private static final Long USER_ID = 1L;

	@Test
	void create_setsAllFields() {
		UserBalance balance = UserBalance.create(USER_ID, AMOUNT);
		assertThat(balance.getUserId()).isEqualTo(USER_ID);
		assertThat(balance.getBalance()).isEqualTo(AMOUNT);
	}

	@Test
	void deduct_reducesBalance() {
		UserBalance balance = UserBalance.create(USER_ID, AMOUNT);
		balance.deduct(Money.of(10_000L));
		assertThat(balance.getBalance()).isEqualTo(Money.zero());
	}

	@Test
	void deduct_whenInsufficientBalance_throwsInsufficientBalanceException() {
		UserBalance balance = UserBalance.create(USER_ID, AMOUNT);
		assertThatThrownBy(() -> balance.deduct(Money.of(10_001L)))
				.isInstanceOf(InsufficientFundsException.class);
	}
}