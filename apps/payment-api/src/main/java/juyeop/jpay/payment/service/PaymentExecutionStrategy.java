package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.entity.Payment;

@FunctionalInterface
public interface PaymentExecutionStrategy {
    Payment execute(String externalId, Long userId, Money amount, String merchantId);
}