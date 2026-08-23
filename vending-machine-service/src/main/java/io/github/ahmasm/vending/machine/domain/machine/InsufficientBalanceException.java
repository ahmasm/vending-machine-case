package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Objects;

public final class InsufficientBalanceException extends RuntimeException {

    private final Money balance;
    private final Money price;

    public InsufficientBalanceException(Money balance, Money price) {
        super("Balance " + balance.amount() + " is below price " + price.amount());
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
    }

    public Money balance() {
        return balance;
    }

    public Money price() {
        return price;
    }
}
