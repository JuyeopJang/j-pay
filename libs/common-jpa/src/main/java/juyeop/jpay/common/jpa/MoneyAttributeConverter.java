package juyeop.jpay.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import juyeop.jpay.common.core.Money;

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