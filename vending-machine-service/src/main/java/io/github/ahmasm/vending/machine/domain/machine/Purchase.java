package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.util.Objects;

public record Purchase(
        TransactionId transactionId,
        MachineId machineId,
        SessionId sessionId,
        SlotCode slotCode,
        ProductSnapshot product,
        Money insertedAmount,
        CashComposition change,
        Instant completedAt) {

    public Purchase {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(slotCode, "slotCode must not be null");
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(insertedAmount, "insertedAmount must not be null");
        Objects.requireNonNull(change, "change must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
