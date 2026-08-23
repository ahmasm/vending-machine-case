package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public record SlotState(SlotCode code, ProductSnapshot product, int quantity) {

    public SlotState {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(product, "product must not be null");
        if (quantity < 0) {
            throw new IllegalArgumentException("Slot quantity must not be negative");
        }
    }
}
