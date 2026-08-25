package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import java.util.Objects;

public record RefundResult(CashComposition returnedCash) implements ProcessedCommandResult {

    public RefundResult {
        Objects.requireNonNull(returnedCash, "returnedCash must not be null");
    }
}
