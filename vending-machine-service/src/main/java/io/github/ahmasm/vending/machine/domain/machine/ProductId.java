package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record ProductId(String value) {

    public ProductId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Product ID must not be blank");
        }
    }
}
