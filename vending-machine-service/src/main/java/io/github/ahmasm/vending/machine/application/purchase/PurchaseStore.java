package io.github.ahmasm.vending.machine.application.purchase;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import java.util.Optional;

public interface PurchaseStore {

    void save(Purchase purchase);

    Optional<Purchase> findById(MachineId machineId, TransactionId transactionId);
}
