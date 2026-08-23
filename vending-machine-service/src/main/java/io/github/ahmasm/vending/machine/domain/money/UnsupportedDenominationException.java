package io.github.ahmasm.vending.machine.domain.money;

import java.util.Objects;

public final class UnsupportedDenominationException extends IllegalArgumentException {

    private final Money value;

    public UnsupportedDenominationException(Money value) {
        super("Unsupported denomination: " + Objects.requireNonNull(value, "value must not be null"));
        this.value = value;
    }

    public Money value() {
        return value;
    }
}
