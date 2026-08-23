package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record SlotCode(String value) {

    public SlotCode {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Slot code must not be blank");
        }
    }
}
