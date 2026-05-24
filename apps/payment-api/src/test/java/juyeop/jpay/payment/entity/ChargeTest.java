package juyeop.jpay.payment.entity;

import juyeop.jpay.common.core.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {

    private static final Money AMOUNT = Money.of(10_000L);
    private static final String EXT_ID = "ext-001";
    private static final Long USER_ID = 1L;
    private static final String BANK_ACCOUNT_ID = "bank-acc-1234567890123456";

    @Test
    void pending_setsAllFields() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        assertThat(charge.getExternalId()).isEqualTo(EXT_ID);
        assertThat(charge.getUserId()).isEqualTo(USER_ID);
        assertThat(charge.getAmount()).isEqualTo(AMOUNT);
        assertThat(charge.getBankAccountId()).isEqualTo(BANK_ACCOUNT_ID);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.getRequestedAt()).isNotNull();
    }

    @Test
    void complete_transitionsToCompleted() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        charge.complete("TRANSFER-001", "{\"raw\":\"ok\"}");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
        assertThat(charge.getTransferRef()).isEqualTo("TRANSFER-001");
        assertThat(charge.getBankResponseMeta()).isEqualTo("{\"raw\":\"ok\"}");
        assertThat(charge.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_whenNotPending_throwsIllegalStateException() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);
        charge.complete("TRANSFER-001", null);

        assertThatThrownBy(() -> charge.complete("TRANSFER-002", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_transitionsToFailed() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        charge.fail("INSUFFICIENT_BALANCE", "{\"code\":\"INSUFFICIENT_BALANCE\"}");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(charge.getFailureReason()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(charge.getBankResponseMeta()).isEqualTo("{\"code\":\"INSUFFICIENT_BALANCE\"}");
        assertThat(charge.getCompletedAt()).isNotNull();
    }

    @Test
    void fail_whenNotPending_throwsIllegalStateException() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);
        charge.fail("INSUFFICIENT_BALANCE", null);

        assertThatThrownBy(() -> charge.fail("SECOND_FAIL", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matches_returnsTrue_whenAllFieldsMatch() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        assertThat(charge.matches(USER_ID, AMOUNT, BANK_ACCOUNT_ID)).isTrue();
    }

    @Test
    void matches_returnsFalse_whenUserIdDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        assertThat(charge.matches(99L, AMOUNT, BANK_ACCOUNT_ID)).isFalse();
    }

    @Test
    void matches_returnsFalse_whenAmountDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        assertThat(charge.matches(USER_ID, Money.of(99_999L), BANK_ACCOUNT_ID)).isFalse();
    }

    @Test
    void matches_returnsFalse_whenBankAccountIdDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, BANK_ACCOUNT_ID);

        assertThat(charge.matches(USER_ID, AMOUNT, "bank-acc-9999999999999999")).isFalse();
    }
}