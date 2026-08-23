package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public final class ProductOutOfStockException extends RuntimeException {

    private final SlotCode slotCode;

    public ProductOutOfStockException(SlotCode slotCode) {
        super("Slot " + slotCode.value() + " is out of stock");
        this.slotCode = Objects.requireNonNull(slotCode, "slotCode must not be null");
    }

    public SlotCode slotCode() {
        return slotCode;
    }
}
