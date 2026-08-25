package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.Objects;

public record InsertMoneyCommand(
        MachineId machineId,
        SessionId sessionId,
        String validatorReference,
        IdempotencyKey idempotencyKey) {

    public InsertMoneyCommand {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(validatorReference, "validatorReference must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        validatorReference = validatorReference.strip();
        if (validatorReference.isEmpty()) {
            throw new IllegalArgumentException("validatorReference must not be blank");
        }
    }
}
