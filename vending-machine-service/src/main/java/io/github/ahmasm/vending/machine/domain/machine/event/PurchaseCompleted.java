package io.github.ahmasm.vending.machine.domain.machine.event;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;
import java.util.Objects;

public record PurchaseCompleted(Purchase purchase) implements VendingMachineEvent {

    public PurchaseCompleted {
        Objects.requireNonNull(purchase, "purchase must not be null");
    }

    @Override
    public MachineId machineId() {
        return purchase.machineId();
    }

    @Override
    public SessionId sessionId() {
        return purchase.sessionId();
    }

    @Override
    public Instant occurredAt() {
        return purchase.completedAt();
    }
}
