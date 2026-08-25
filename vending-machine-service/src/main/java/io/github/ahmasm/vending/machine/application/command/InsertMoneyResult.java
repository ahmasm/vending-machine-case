package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Objects;

public record InsertMoneyResult(Money balance) implements ProcessedCommandResult {

    public InsertMoneyResult {
        Objects.requireNonNull(balance, "balance must not be null");
    }
}
