package juyeop.jpay.payment.entity;

import juyeop.jpay.common.core.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {

    private static final Money AMOUNT = Money.of(10_000L);
    private static final String EXT_ID = "ext-001";
    private static final Long USER_ID = 1L;
    private static final String PM_ID = "card-1234567890123456";

    @Test
    void pending_setsAllFields() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        assertThat(charge.getExternalId()).isEqualTo(EXT_ID);
        assertThat(charge.getUserId()).isEqualTo(USER_ID);
        assertThat(charge.getAmount()).isEqualTo(AMOUNT);
        assertThat(charge.getPaymentMethodId()).isEqualTo(PM_ID);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(charge.getRequestedAt()).isNotNull();
    }

    @Test
    void complete_transitionsToCompleted() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        charge.complete("PG-APPROVAL-001", "{\"raw\":\"ok\"}");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
        assertThat(charge.getPgApprovalNumber()).isEqualTo("PG-APPROVAL-001");
        assertThat(charge.getPgResponseMeta()).isEqualTo("{\"raw\":\"ok\"}");
        assertThat(charge.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_whenNotPending_throwsIllegalStateException() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);
        charge.complete("PG-001", null);

        assertThatThrownBy(() -> charge.complete("PG-002", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_transitionsToFailed() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        charge.fail("LIMIT_EXCEEDED", "{\"code\":\"LIMIT\"}");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(charge.getFailureReason()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(charge.getPgResponseMeta()).isEqualTo("{\"code\":\"LIMIT\"}");
        assertThat(charge.getCompletedAt()).isNotNull();
    }

    @Test
    void fail_whenNotPending_throwsIllegalStateException() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);
        charge.fail("LIMIT_EXCEEDED", null);

        assertThatThrownBy(() -> charge.fail("SECOND_FAIL", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matches_returnsTrue_whenAllFieldsMatch() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        assertThat(charge.matches(USER_ID, AMOUNT, PM_ID)).isTrue();
    }

    @Test
    void matches_returnsFalse_whenUserIdDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        assertThat(charge.matches(99L, AMOUNT, PM_ID)).isFalse();
    }

    @Test
    void matches_returnsFalse_whenAmountDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        assertThat(charge.matches(USER_ID, Money.of(99_999L), PM_ID)).isFalse();
    }

    @Test
    void matches_returnsFalse_whenPaymentMethodIdDiffers() {
        Charge charge = Charge.pending(EXT_ID, USER_ID, AMOUNT, PM_ID);

        assertThat(charge.matches(USER_ID, AMOUNT, "card-9999999999999999")).isFalse();
    }
}