package io.github.ahmasm.vending.machine.application.port.in;

import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import java.util.Objects;

public record SelectProductResult(Purchase purchase) implements ProcessedCommandResult {

    public SelectProductResult {
        Objects.requireNonNull(purchase, "purchase must not be null");
    }
}
