package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record VendingMachineState(
        MachineId id,
        CashComposition cashInventory,
        List<SlotState> slots,
        Optional<PurchaseSessionState> currentSession) {

    public VendingMachineState {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(cashInventory, "cashInventory must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
        currentSession = Objects.requireNonNull(currentSession, "currentSession must not be null");
    }
}
