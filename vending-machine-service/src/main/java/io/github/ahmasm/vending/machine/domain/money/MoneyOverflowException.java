package io.github.ahmasm.vending.machine.domain.money;

public final class MoneyOverflowException extends ArithmeticException {

    public MoneyOverflowException(String message, ArithmeticException cause) {
        super(message);
        initCause(cause);
    }
}
