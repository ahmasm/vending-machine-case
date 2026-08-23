package io.github.ahmasm.vending.machine.domain.cash;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class CashComposition {

    private static final CashComposition EMPTY = new CashComposition(Map.of());

    private final Map<Denomination, Integer> quantities;
    private final Money total;
    private final long pieceCount;

    private CashComposition(Map<Denomination, Integer> source) {
        Objects.requireNonNull(source, "source must not be null");

        var normalized = new EnumMap<Denomination, Integer>(Denomination.class);
        long amount = 0;
        long pieces = 0;

        for (var entry : source.entrySet()) {
            var denomination = Objects.requireNonNull(entry.getKey(), "denomination must not be null");
            var quantity = Objects.requireNonNull(entry.getValue(), "quantity must not be null");
            if (quantity < 0) {
                throw new NegativeDenominationQuantityException(denomination, quantity);
            }
            if (quantity == 0) {
                continue;
            }

            normalized.put(denomination, quantity);
            pieces = Math.addExact(pieces, quantity.longValue());
            amount = Math.addExact(
                    amount,
                    Math.multiplyExact(denomination.value().amount(), quantity.longValue()));
        }

        quantities = Collections.unmodifiableMap(normalized);
        total = Money.of(amount, UNIT);
        pieceCount = pieces;
    }

    public static CashComposition empty() {
        return EMPTY;
    }

    public static CashComposition of(Map<Denomination, Integer> quantities) {
        var composition = new CashComposition(quantities);
        return composition.isEmpty() ? EMPTY : composition;
    }

    public CashComposition add(Denomination denomination) {
        Objects.requireNonNull(denomination, "denomination must not be null");

        var updated = new EnumMap<Denomination, Integer>(Denomination.class);
        updated.putAll(quantities);
        updated.put(denomination, Math.addExact(quantityOf(denomination), 1));
        return CashComposition.of(updated);
    }

    public CashComposition add(CashComposition addend) {
        Objects.requireNonNull(addend, "addend must not be null");

        var updated = new EnumMap<Denomination, Integer>(Denomination.class);
        updated.putAll(quantities);
        for (var denomination : Denomination.values()) {
            var addedQuantity = addend.quantityOf(denomination);
            if (addedQuantity > 0) {
                updated.put(
                        denomination,
                        Math.addExact(quantityOf(denomination), addedQuantity));
            }
        }
        return CashComposition.of(updated);
    }

    public CashComposition subtract(CashComposition subtrahend) {
        Objects.requireNonNull(subtrahend, "subtrahend must not be null");

        var updated = new EnumMap<Denomination, Integer>(Denomination.class);
        updated.putAll(quantities);
        for (var denomination : Denomination.values()) {
            var remaining = quantityOf(denomination) - subtrahend.quantityOf(denomination);
            if (remaining < 0) {
                throw new IllegalArgumentException(
                        "Cannot subtract more " + denomination + " pieces than available");
            }
            updated.put(denomination, remaining);
        }
        return CashComposition.of(updated);
    }

    public int quantityOf(Denomination denomination) {
        Objects.requireNonNull(denomination, "denomination must not be null");
        return quantities.getOrDefault(denomination, 0);
    }

    public Money total() {
        return total;
    }

    public long pieceCount() {
        return pieceCount;
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof CashComposition other
                        && quantities.equals(other.quantities);
    }

    @Override
    public int hashCode() {
        return quantities.hashCode();
    }

    @Override
    public String toString() {
        return "CashComposition" + quantities;
    }
}
