package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public record StartSessionCommand(
        MachineId machineId,
        IdempotencyKey idempotencyKey) {

    public StartSessionCommand {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
