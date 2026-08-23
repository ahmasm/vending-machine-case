package io.github.ahmasm.vending.machine.domain.cash;

import io.github.ahmasm.vending.machine.domain.money.Denomination;

public final class NegativeDenominationQuantityException extends IllegalArgumentException {

    private final Denomination denomination;
    private final int quantity;

    public NegativeDenominationQuantityException(Denomination denomination, int quantity) {
        super("Denomination quantity must not be negative: " + denomination + "=" + quantity);
        this.denomination = denomination;
        this.quantity = quantity;
    }

    public Denomination denomination() {
        return denomination;
    }

    public int quantity() {
        return quantity;
    }
}
