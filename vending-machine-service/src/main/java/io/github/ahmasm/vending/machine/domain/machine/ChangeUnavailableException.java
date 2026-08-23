package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Objects;

public final class ChangeUnavailableException extends RuntimeException {

    private final Money changeDue;

    public ChangeUnavailableException(Money changeDue) {
        super("Exact change is unavailable for amount " + changeDue.amount());
        this.changeDue = Objects.requireNonNull(changeDue, "changeDue must not be null");
    }

    public Money changeDue() {
        return changeDue;
    }
}
