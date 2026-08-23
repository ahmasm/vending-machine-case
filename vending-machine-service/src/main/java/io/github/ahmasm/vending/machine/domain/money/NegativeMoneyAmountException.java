package io.github.ahmasm.vending.machine.domain.money;

public final class NegativeMoneyAmountException extends IllegalArgumentException {

    private final long amount;

    public NegativeMoneyAmountException(long amount) {
        super("Money amount must not be negative: " + amount);
        this.amount = amount;
    }

    public long amount() {
        return amount;
    }
}
