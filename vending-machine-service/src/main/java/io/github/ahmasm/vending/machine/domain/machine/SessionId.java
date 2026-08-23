package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record SessionId(String value) {

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Session ID must not be blank");
        }
    }
}
