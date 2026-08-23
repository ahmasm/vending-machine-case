package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public final class SlotNotFoundException extends RuntimeException {

    private final SlotCode slotCode;

    public SlotNotFoundException(SlotCode slotCode) {
        super("Slot " + slotCode.value() + " does not exist");
        this.slotCode = Objects.requireNonNull(slotCode, "slotCode must not be null");
    }

    public SlotCode slotCode() {
        return slotCode;
    }
}
