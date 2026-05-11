package juyeop.jpay.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import juyeop.jpay.common.core.Money;

/**
 * Money VO ↔ BIGINT 단일 컬럼 자동 매핑. autoApply로 Money 타입 필드는 자동 적용.
 */
@Converter(autoApply = true)
public class MoneyAttributeConverter implements AttributeConverter<Money, Long> {

    @Override
    public Long convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(Long amount) {
        return amount == null ? null : Money.of(amount);
    }
}