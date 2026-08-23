package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record TransactionId(String value) {

    public TransactionId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Transaction ID must not be blank");
        }
    }
}
