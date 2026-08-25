package io.github.ahmasm.vending.machine.application.command;

import java.util.Objects;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
    }
}
