package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public final class IdempotencyKeyReusedException extends RuntimeException {

    private final MachineId machineId;

    public IdempotencyKeyReusedException(MachineId machineId) {
        super("Idempotency key was already used for another command on machine "
                + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }
}
