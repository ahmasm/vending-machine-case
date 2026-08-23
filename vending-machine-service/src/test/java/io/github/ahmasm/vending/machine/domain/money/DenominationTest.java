package io.github.ahmasm.vending.machine.domain.money;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DenominationTest {

    @Test
    void currencyModelSupportsOnlyUnit() {
        assertArrayEquals(new Currency[] {UNIT}, Currency.values());
    }

    @ParameterizedTest
    @CsvSource({"5, FIVE", "10, TEN", "20, TWENTY", "50, FIFTY"})
    void resolvingSupportedUnitAmountReturnsItsDenomination(long amount, Denomination expected) {
        var denomination = Denomination.from(Money.of(amount, UNIT));

        assertEquals(expected, denomination);
        assertEquals(Money.of(amount, UNIT), denomination.value());
    }

    @Test
    void resolvingUnsupportedUnitAmountIsRejected() {
        var unsupportedValue = Money.of(1, UNIT);

        var exception = assertThrows(
                UnsupportedDenominationException.class,
                () -> Denomination.from(unsupportedValue));

        assertEquals(unsupportedValue, exception.value());
    }
}
