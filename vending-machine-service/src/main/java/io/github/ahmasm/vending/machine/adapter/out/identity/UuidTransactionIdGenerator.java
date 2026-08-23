package io.github.ahmasm.vending.machine.adapter.out.identity;

import io.github.ahmasm.vending.machine.application.port.out.TransactionIdGenerator;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class UuidTransactionIdGenerator implements TransactionIdGenerator {

    @Override
    public TransactionId next() {
        return new TransactionId(UUID.randomUUID().toString());
    }
}
