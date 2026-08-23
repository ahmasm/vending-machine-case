package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.TransactionId;

public interface TransactionIdGenerator {

    TransactionId next();
}
