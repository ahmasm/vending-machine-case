package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import java.util.Objects;

public record SelectProductResult(Purchase purchase) implements ProcessedCommandResult {

    public SelectProductResult {
        Objects.requireNonNull(purchase, "purchase must not be null");
    }
}
