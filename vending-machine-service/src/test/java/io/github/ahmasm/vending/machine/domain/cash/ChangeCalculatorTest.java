package io.github.ahmasm.vending.machine.domain.cash;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TWENTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeCalculatorTest {

    private final ChangeCalculator calculator = new ChangeCalculator();

    @Test
    void calculatingZeroChangeReturnsEmptyCompositionWithoutRequiringCash() {
        var result = calculator.calculate(unit(0), CashComposition.empty());

        assertEquals(CashComposition.empty(), result.orElseThrow());
    }

    @Test
    void calculatingExactChangeUsesOnlyAvailableQuantities() {
        var available = CashComposition.of(Map.of(FIFTY, 2, TEN, 1, FIVE, 3));

        var result = calculator.calculate(unit(15), available);

        assertEquals(CashComposition.of(Map.of(TEN, 1, FIVE, 1)), result.orElseThrow());
    }

    @Test
    void calculatingBoundedChangeBacktracksWhenLargestDenominationBlocksExactResult() {
        var available = CashComposition.of(Map.of(FIFTY, 1, TWENTY, 3));

        var result = calculator.calculate(unit(60), available);

        assertEquals(CashComposition.of(Map.of(TWENTY, 3)), result.orElseThrow());
    }

    @Test
    void calculatingChangeMinimizesTheNumberOfPieces() {
        var available = CashComposition.of(Map.of(FIFTY, 1, TWENTY, 3, TEN, 1));

        var result = calculator.calculate(unit(60), available);

        assertEquals(CashComposition.of(Map.of(FIFTY, 1, TEN, 1)), result.orElseThrow());
    }

    @Test
    void calculatingChangeWithEqualPieceCountsPrefersHigherDenominations() {
        var available = CashComposition.of(Map.of(FIFTY, 1, TWENTY, 3, FIVE, 2));

        var result = calculator.calculate(unit(60), available);

        assertEquals(CashComposition.of(Map.of(FIFTY, 1, FIVE, 2)), result.orElseThrow());
    }

    @Test
    void calculatingUnavailableExactChangeReturnsNoComposition() {
        var available = CashComposition.of(Map.of(TWENTY, 1, TEN, 1));

        var result = calculator.calculate(unit(25), available);

        assertTrue(result.isEmpty());
    }

    @Test
    void calculatingChangeMatchesExhaustiveOracleAcrossSmallBoundedInventories() {
        for (var fiftyCount = 0; fiftyCount <= 2; fiftyCount++) {
            for (var twentyCount = 0; twentyCount <= 2; twentyCount++) {
                for (var tenCount = 0; tenCount <= 2; tenCount++) {
                    for (var fiveCount = 0; fiveCount <= 2; fiveCount++) {
                        var available = composition(fiftyCount, twentyCount, tenCount, fiveCount);
                        for (long amount = 0; amount <= available.total().amount() + 5; amount++) {
                            assertEquals(
                                    exhaustiveChange(amount, available),
                                    calculator.calculate(unit(amount), available),
                                    "Unexpected change for amount " + amount + " from " + available);
                        }
                    }
                }
            }
        }
    }

    private static Optional<CashComposition> exhaustiveChange(
            long amount, CashComposition available) {
        CashComposition best = null;

        for (var fiftyCount = 0; fiftyCount <= available.quantityOf(FIFTY); fiftyCount++) {
            for (var twentyCount = 0; twentyCount <= available.quantityOf(TWENTY); twentyCount++) {
                for (var tenCount = 0; tenCount <= available.quantityOf(TEN); tenCount++) {
                    for (var fiveCount = 0; fiveCount <= available.quantityOf(FIVE); fiveCount++) {
                        var candidate = composition(fiftyCount, twentyCount, tenCount, fiveCount);
                        if (candidate.total().amount() == amount && isBetter(candidate, best)) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static boolean isBetter(CashComposition candidate, CashComposition currentBest) {
        if (currentBest == null || candidate.pieceCount() < currentBest.pieceCount()) {
            return true;
        }
        if (candidate.pieceCount() > currentBest.pieceCount()) {
            return false;
        }

        for (var denomination : List.of(FIFTY, TWENTY, TEN, FIVE)) {
            if (candidate.quantityOf(denomination) != currentBest.quantityOf(denomination)) {
                return candidate.quantityOf(denomination) > currentBest.quantityOf(denomination);
            }
        }
        return false;
    }

    private static CashComposition composition(
            int fiftyCount, int twentyCount, int tenCount, int fiveCount) {
        return CashComposition.of(Map.of(
                FIFTY, fiftyCount,
                TWENTY, twentyCount,
                TEN, tenCount,
                FIVE, fiveCount));
    }

    private static Money unit(long amount) {
        return Money.of(amount, UNIT);
    }
}
