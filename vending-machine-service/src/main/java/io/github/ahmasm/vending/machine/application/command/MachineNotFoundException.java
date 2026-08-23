package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public final class MachineNotFoundException extends RuntimeException {

    private final MachineId machineId;

    public MachineNotFoundException(MachineId machineId) {
        super("Machine " + machineId.value() + " was not found");
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }
}
