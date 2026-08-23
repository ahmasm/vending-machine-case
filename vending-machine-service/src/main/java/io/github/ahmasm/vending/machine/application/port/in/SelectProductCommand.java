package io.github.ahmasm.vending.machine.application.port.in;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import java.util.Objects;

public record SelectProductCommand(
        MachineId machineId,
        SessionId sessionId,
        SlotCode slotCode,
        IdempotencyKey idempotencyKey) {

    public SelectProductCommand {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(slotCode, "slotCode must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
