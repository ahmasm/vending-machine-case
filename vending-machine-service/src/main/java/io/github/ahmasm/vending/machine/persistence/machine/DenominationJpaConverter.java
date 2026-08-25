package io.github.ahmasm.vending.machine.persistence.machine;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public final class DenominationJpaConverter
        implements AttributeConverter<Denomination, Short> {

    @Override
    public Short convertToDatabaseColumn(Denomination denomination) {
        return denomination == null ? null : (short) denomination.value().amount();
    }

    @Override
    public Denomination convertToEntityAttribute(Short amount) {
        return amount == null ? null : Denomination.from(Money.of(amount, UNIT));
    }
}
