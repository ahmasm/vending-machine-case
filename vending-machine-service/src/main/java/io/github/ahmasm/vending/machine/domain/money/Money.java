package io.github.ahmasm.vending.machine.domain.money;

import java.util.Objects;

public record Money(long amount, Currency currency) {

    public Money {
        if (amount < 0) {
            throw new NegativeMoneyAmountException(amount);
        }
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money of(long amount, Currency currency) {
        return new Money(amount, currency);
    }

    public Money add(Money addend) {
        Objects.requireNonNull(addend, "addend must not be null");

        try {
            return Money.of(Math.addExact(amount, addend.amount), currency);
        } catch (ArithmeticException exception) {
            throw new MoneyOverflowException("Money addition exceeds the long range", exception);
        }
    }

    public Money subtract(Money subtrahend) {
        Objects.requireNonNull(subtrahend, "subtrahend must not be null");

        try {
            return Money.of(Math.subtractExact(amount, subtrahend.amount), currency);
        } catch (ArithmeticException exception) {
            throw new MoneyOverflowException("Money subtraction exceeds the long range", exception);
        }
    }
}
