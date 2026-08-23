package io.github.ahmasm.vending.machine.domain.cash;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CashCompositionTest {

    @Test
    void creatingEmptyCompositionHasZeroValueAndNoPieces() {
        var composition = CashComposition.empty();

        assertTrue(composition.isEmpty());
        assertEquals(Money.of(0, UNIT), composition.total());
        assertEquals(0, composition.pieceCount());
        assertEquals(0, composition.quantityOf(FIVE));
    }

    @Test
    void creatingCompositionDerivesTotalAndPieceCountFromQuantities() {
        var composition = CashComposition.of(Map.of(FIFTY, 1, TEN, 2, FIVE, 3));

        assertEquals(Money.of(85, UNIT), composition.total());
        assertEquals(6, composition.pieceCount());
        assertEquals(1, composition.quantityOf(FIFTY));
        assertEquals(0, composition.quantityOf(Denomination.TWENTY));
    }

    @Test
    void creatingCompositionMakesADefensiveCopyOfQuantities() {
        var source = new EnumMap<Denomination, Integer>(Denomination.class);
        source.put(TEN, 2);

        var composition = CashComposition.of(source);
        source.put(TEN, 9);

        assertEquals(2, composition.quantityOf(TEN));
    }

    @Test
    void creatingCompositionWithZeroQuantitiesCanonicalizesToEmpty() {
        var composition = CashComposition.of(Map.of(FIVE, 0, TEN, 0));

        assertEquals(CashComposition.empty(), composition);
        assertTrue(composition.isEmpty());
    }

    @Test
    void creatingCompositionWithNegativeQuantityIsRejected() {
        var exception = assertThrows(
                NegativeDenominationQuantityException.class,
                () -> CashComposition.of(Map.of(TEN, -1)));

        assertEquals(TEN, exception.denomination());
        assertEquals(-1, exception.quantity());
    }
}
