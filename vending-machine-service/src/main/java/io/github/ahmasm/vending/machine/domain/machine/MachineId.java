package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record MachineId(String value) {

    public MachineId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Machine ID must not be blank");
        }
    }
}
