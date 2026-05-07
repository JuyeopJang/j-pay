package juyeop.jpay.common.core;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(of = "amount")
public final class Money {

    public static final Money ZERO = new Money(0L);

    private final long amount;

    private Money(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Money must be non-negative: " + amount);
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        return amount == 0 ? ZERO : new Money(amount);
    }

    public static Money zero() {
        return ZERO;
    }

    public long amount() {
        return amount;
    }

    public Money plus(Money other) {
        return Money.of(Math.addExact(this.amount, other.amount));
    }

    public Money minus(Money other) {
        if (other.amount > this.amount) {
            throw new InsufficientFundsException(this, other);
        }
        return Money.of(this.amount - other.amount);
    }

    public boolean isZero() {
        return amount == 0L;
    }

    public boolean isPositive() {
        return amount > 0L;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isLessThan(Money other) {
        return this.amount < other.amount;
    }

    @Override
    public String toString() {
        return amount + " KRW";
    }
}