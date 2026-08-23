package io.github.ahmasm.vending.machine.application.purchase;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import java.util.Objects;

public final class PurchaseNotFoundException extends RuntimeException {

    private final MachineId machineId;
    private final TransactionId transactionId;

    public PurchaseNotFoundException(MachineId machineId, TransactionId transactionId) {
        super("Purchase " + transactionId.value()
                + " was not found for machine "
                + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        this.transactionId =
                Objects.requireNonNull(transactionId, "transactionId must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }

    public TransactionId transactionId() {
        return transactionId;
    }
}
