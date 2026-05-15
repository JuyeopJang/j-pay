package juyeop.jpay.payment.entity;

import juyeop.jpay.common.core.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

	private static final Money AMOUNT = Money.of(10_000L);
	private static final String EXT_ID = "ext-001";
	private static final Long USER_ID = 1L;
	private static final String M_ID = "merchant-001";

	@Test
	void pending_setsAllFields() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		assertThat(payment.getExternalId()).isEqualTo(EXT_ID);
		assertThat(payment.getUserId()).isEqualTo(USER_ID);
		assertThat(payment.getAmount()).isEqualTo(AMOUNT);
		assertThat(payment.getMerchantId()).isEqualTo(M_ID);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(payment.getRequestedAt()).isNotNull();
	}

	@Test
	void complete_transitionsToCompleted() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		payment.complete();

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
		assertThat(payment.getCompletedAt()).isNotNull();
	}

	@Test
	void complete_whenNotPending_throwsIllegalStateException() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);
		payment.complete();

		assertThatThrownBy(payment::complete)
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void fail_transitionsToFailed() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		payment.fail("LIMIT_EXCEEDED");

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getFailureReason()).isEqualTo("LIMIT_EXCEEDED");
		assertThat(payment.getCompletedAt()).isNotNull();
	}

	@Test
	void fail_whenNotPending_throwsIllegalStateException() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);
		payment.fail("LIMIT_EXCEEDED");

		assertThatThrownBy(() -> payment.fail("LIMIT_EXCEEDED"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void matches_returnsTrue_whenAllFieldsMatch() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		assertThat(payment.matches(USER_ID, AMOUNT, M_ID)).isTrue();
	}

	@Test
	void matches_returnsFalse_whenUserIdDiffers() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		assertThat(payment.matches(99L, AMOUNT, M_ID)).isFalse();
	}

	@Test
	void matches_returnsFalse_whenAmountDiffers() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		assertThat(payment.matches(USER_ID, Money.of(99_999L), M_ID)).isFalse();
	}

	@Test
	void matches_returnsFalse_whenMerchantIdDiffers() {
		Payment payment = Payment.pending(EXT_ID, USER_ID, AMOUNT, M_ID);

		assertThat(payment.matches(USER_ID, AMOUNT, "card-9999999999999999")).isFalse();
	}

}