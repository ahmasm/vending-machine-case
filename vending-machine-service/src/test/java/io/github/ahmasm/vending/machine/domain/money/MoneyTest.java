package io.github.ahmasm.vending.machine.domain.money;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void creatingMoneyWithNegativeMinorUnitsIsRejected() {
        var exception = assertThrows(NegativeMoneyAmountException.class, () -> unit(-1));

        assertEquals(-1, exception.amount());
    }

    @Test
    void creatingMoneyAtMaximumLongValuePreservesTheExactAmount() {
        var money = unit(Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, money.amount());
        assertSame(UNIT, money.currency());
    }

    @Test
    void addingMoneyCombinesMinorUnitsWithoutLosingPrecision() {
        var result = unit(20).add(unit(50));

        assertEquals(unit(70), result);
    }

    @Test
    void addingMoneyBeyondLongRangeIsRejected() {
        var maximum = unit(Long.MAX_VALUE);

        assertThrows(MoneyOverflowException.class, () -> maximum.add(unit(1)));
    }

    @Test
    void subtractingMoneyReturnsTheNonNegativeDifference() {
        var result = unit(50).subtract(unit(20));

        assertEquals(unit(30), result);
    }

    @Test
    void subtractingMoreMoneyThanAvailableIsRejected() {
        var exception = assertThrows(
                NegativeMoneyAmountException.class,
                () -> unit(20).subtract(unit(50)));

        assertEquals(-30, exception.amount());
    }

    private static Money unit(long amount) {
        return Money.of(amount, UNIT);
    }
}
