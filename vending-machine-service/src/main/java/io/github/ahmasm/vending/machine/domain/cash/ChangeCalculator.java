package io.github.ahmasm.vending.machine.domain.cash;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ChangeCalculator {

    private static final List<Denomination> DESCENDING_DENOMINATIONS = Arrays.stream(Denomination.values())
            .sorted(Comparator.comparingLong((Denomination denomination) -> denomination.value().amount())
                    .reversed())
            .toList();

    public Optional<CashComposition> calculate(Money changeDue, CashComposition available) {
        Objects.requireNonNull(changeDue, "changeDue must not be null");
        Objects.requireNonNull(available, "available must not be null");

        if (changeDue.amount() == 0) {
            return Optional.of(CashComposition.empty());
        }
        if (changeDue.amount() > available.total().amount()) {
            return Optional.empty();
        }

        var search = new BoundedChangeSearch(changeDue.amount(), available);
        return search.calculate();
    }

    private static final class BoundedChangeSearch {

        private final CashComposition available;
        private final int[] currentQuantities = new int[DESCENDING_DENOMINATIONS.size()];
        private int[] bestQuantities;
        private long bestPieceCount = Long.MAX_VALUE;

        private BoundedChangeSearch(long changeDue, CashComposition available) {
            this.available = available;
            this.changeDue = changeDue;
        }

        private final long changeDue;

        private Optional<CashComposition> calculate() {
            search(0, changeDue, 0);
            if (bestQuantities == null) {
                return Optional.empty();
            }

            var result = new EnumMap<Denomination, Integer>(Denomination.class);
            for (var index = 0; index < DESCENDING_DENOMINATIONS.size(); index++) {
                if (bestQuantities[index] > 0) {
                    result.put(DESCENDING_DENOMINATIONS.get(index), bestQuantities[index]);
                }
            }
            return Optional.of(CashComposition.of(result));
        }

        private void search(int index, long remaining, long usedPieces) {
            if (remaining == 0) {
                keepIfBetter(usedPieces);
                return;
            }
            if (index == DESCENDING_DENOMINATIONS.size()) {
                return;
            }

            var denomination = DESCENDING_DENOMINATIONS.get(index);
            var denominationAmount = denomination.value().amount();
            var maximumQuantity = (int) Math.min(
                    available.quantityOf(denomination),
                    remaining / denominationAmount);

            for (var quantity = maximumQuantity; quantity >= 0; quantity--) {
                var nextRemaining = remaining - denominationAmount * quantity;
                var nextPieceCount = usedPieces + quantity;

                if (cannotBeatCurrentBest(index, nextRemaining, nextPieceCount)) {
                    continue;
                }
                if (nextRemaining > availableValueAfter(index)) {
                    continue;
                }

                currentQuantities[index] = quantity;
                search(index + 1, nextRemaining, nextPieceCount);
            }
            currentQuantities[index] = 0;
        }

        private boolean cannotBeatCurrentBest(int index, long remaining, long usedPieces) {
            if (bestQuantities == null || remaining == 0) {
                return false;
            }
            if (index + 1 == DESCENDING_DENOMINATIONS.size()) {
                return usedPieces >= bestPieceCount;
            }

            var largestRemainingAmount = DESCENDING_DENOMINATIONS.get(index + 1).value().amount();
            var optimisticAdditionalPieces = Math.ceilDiv(remaining, largestRemainingAmount);
            return usedPieces + optimisticAdditionalPieces > bestPieceCount;
        }

        private long availableValueAfter(int index) {
            long value = 0;
            for (var lowerIndex = index + 1;
                    lowerIndex < DESCENDING_DENOMINATIONS.size();
                    lowerIndex++) {
                var denomination = DESCENDING_DENOMINATIONS.get(lowerIndex);
                value = Math.addExact(
                        value,
                        Math.multiplyExact(
                                denomination.value().amount(),
                                available.quantityOf(denomination)));
            }
            return value;
        }

        private void keepIfBetter(long pieceCount) {
            if (pieceCount < bestPieceCount
                    || pieceCount == bestPieceCount && prefersCurrentHigherDenominations()) {
                bestPieceCount = pieceCount;
                bestQuantities = currentQuantities.clone();
            }
        }

        private boolean prefersCurrentHigherDenominations() {
            if (bestQuantities == null) {
                return true;
            }
            for (var index = 0; index < currentQuantities.length; index++) {
                if (currentQuantities[index] != bestQuantities[index]) {
                    return currentQuantities[index] > bestQuantities[index];
                }
            }
            return false;
        }
    }
}
