package io.github.ahmasm.vending.machine.domain.money;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;

import java.util.Objects;

public enum Denomination {
    FIVE(5),
    TEN(10),
    TWENTY(20),
    FIFTY(50);

    private final Money value;

    Denomination(long amount) {
        value = Money.of(amount, UNIT);
    }

    public Money value() {
        return value;
    }

    public static Denomination from(Money value) {
        Objects.requireNonNull(value, "value must not be null");

        for (var denomination : values()) {
            if (denomination.value.equals(value)) {
                return denomination;
            }
        }

        throw new UnsupportedDenominationException(value);
    }
}
