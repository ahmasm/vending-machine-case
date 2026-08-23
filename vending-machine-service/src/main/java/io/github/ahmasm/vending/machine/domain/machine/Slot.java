package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Objects;

public final class Slot {

    private final SlotCode code;
    private final ProductSnapshot product;
    private int quantity;

    public Slot(SlotCode code, ProductSnapshot product, int quantity) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.product = Objects.requireNonNull(product, "product must not be null");
        if (quantity < 0) {
            throw new IllegalArgumentException("Slot quantity must not be negative");
        }
        this.quantity = quantity;
    }

    public SlotCode code() {
        return code;
    }

    public ProductSnapshot product() {
        return product;
    }

    public int quantity() {
        return quantity;
    }

    SlotState state() {
        return new SlotState(code, product, quantity);
    }

    Slot copy() {
        return new Slot(code, product, quantity);
    }

    void dispenseOne() {
        if (quantity == 0) {
            throw new ProductOutOfStockException(code);
        }
        quantity--;
    }
}
