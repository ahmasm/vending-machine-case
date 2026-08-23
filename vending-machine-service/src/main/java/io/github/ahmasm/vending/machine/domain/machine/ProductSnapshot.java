package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Objects;

public record ProductSnapshot(ProductId id, String name, Money price) {

    public ProductSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (price.amount() == 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
    }
}
