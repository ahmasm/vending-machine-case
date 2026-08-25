package io.github.ahmasm.vending.machine.application.purchase;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class GetPurchaseHandler {

    private final PurchaseStore purchaseStore;

    public GetPurchaseHandler(PurchaseStore purchaseStore) {
        this.purchaseStore =
                Objects.requireNonNull(purchaseStore, "purchaseStore must not be null");
    }

    public Purchase handle(MachineId machineId, TransactionId transactionId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        return purchaseStore
                .findById(machineId, transactionId)
                .orElseThrow(() -> new PurchaseNotFoundException(machineId, transactionId));
    }
}
