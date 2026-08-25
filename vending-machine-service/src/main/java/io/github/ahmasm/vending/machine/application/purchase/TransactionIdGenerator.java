package io.github.ahmasm.vending.machine.application.purchase;

import io.github.ahmasm.vending.machine.domain.machine.TransactionId;

public interface TransactionIdGenerator {

    TransactionId next();
}
