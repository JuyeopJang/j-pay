package juyeop.jpay.common.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Nested
    @DisplayName("생성")
    class Construction {

        @Test
        @DisplayName("0원으로 생성 가능 (하한 경계)")
        void create_zero_amount() {
            Money m = Money.of(0L);
            assertThat(m.amount()).isZero();
        }

        @Test
        @DisplayName("음수 -1원 생성 시 IllegalArgumentException (경계 직하)")
        void reject_negative_one() {
            assertThatThrownBy(() -> Money.of(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Long.MIN_VALUE 생성 시 IllegalArgumentException (극단 음수)")
        void reject_long_min_value() {
            assertThatThrownBy(() -> Money.of(Long.MIN_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Long.MAX_VALUE 생성 가능 (상한 경계)")
        void create_long_max_value() {
            Money m = Money.of(Long.MAX_VALUE);
            assertThat(m.amount()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("Money.of(0)은 ZERO 싱글턴과 동일 인스턴스")
        void zero_singleton() {
            assertThat(Money.of(0L)).isSameAs(Money.zero());
        }
    }

    @Nested
    @DisplayName("plus")
    class Plus {

        @Test
        @DisplayName("zero + zero = zero")
        void zero_plus_zero() {
            assertThat(Money.zero().plus(Money.zero())).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("Long.MAX_VALUE + 1 시 ArithmeticException (overflow 경계)")
        void overflow_throws() {
            Money max = Money.of(Long.MAX_VALUE);
            Money one = Money.of(1L);
            assertThatThrownBy(() -> max.plus(one))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("Long.MAX_VALUE + 0 은 정상 (overflow 직전 경계)")
        void max_plus_zero_ok() {
            Money max = Money.of(Long.MAX_VALUE);
            assertThat(max.plus(Money.zero()).amount()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("minus")
    class Minus {

        @Test
        @DisplayName("동일 금액 차감 시 zero (정확히 일치 경계)")
        void exact_subtract_yields_zero() {
            Money a = Money.of(100L);
            assertThat(a.minus(a)).isEqualTo(Money.zero());
        }

        @Test
        @DisplayName("1원 부족 시 InsufficientFundsException (경계 직하)")
        void one_short_throws() {
            Money a = Money.of(100L);
            Money b = Money.of(101L);
            assertThatThrownBy(() -> a.minus(b))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("zero에서 1원 차감 시 예외 (zero 경계)")
        void zero_minus_one_throws() {
            assertThatThrownBy(() -> Money.zero().minus(Money.of(1L)))
                    .isInstanceOf(InsufficientFundsException.class);
        }
    }

    @Nested
    @DisplayName("비교")
    class Comparison {

        @Test
        @DisplayName("같은 금액끼리 isGreaterThanOrEqual = true (등치 경계)")
        void equal_amount_ge_true() {
            assertThat(Money.of(100L).isGreaterThanOrEqual(Money.of(100L))).isTrue();
        }

        @Test
        @DisplayName("같은 금액끼리 isLessThan = false (등치 경계)")
        void equal_amount_lt_false() {
            assertThat(Money.of(100L).isLessThan(Money.of(100L))).isFalse();
        }

        @Test
        @DisplayName("zero.isPositive() = false (양수 경계 하단)")
        void zero_not_positive() {
            assertThat(Money.zero().isPositive()).isFalse();
        }

        @Test
        @DisplayName("1원.isPositive() = true (양수 경계)")
        void one_is_positive() {
            assertThat(Money.of(1L).isPositive()).isTrue();
        }

        @Test
        @DisplayName("zero.isZero() = true / 1원.isZero() = false")
        void is_zero_check() {
            assertThat(Money.zero().isZero()).isTrue();
            assertThat(Money.of(1L).isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("equals / hashCode")
    class Equality {

        @Test
        @DisplayName("amount 같으면 equals = true, hashCode 동일")
        void equal_amount_equals_and_same_hash() {
            Money a = Money.of(1_000L);
            Money b = Money.of(1_000L);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("amount 다르면 equals = false")
        void different_amount_not_equals() {
            assertThat(Money.of(100L)).isNotEqualTo(Money.of(101L));
        }
    }
}