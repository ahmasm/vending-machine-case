package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public final class CurrencyValidationUnavailableException extends RuntimeException {

    private final MachineId machineId;

    public CurrencyValidationUnavailableException(MachineId machineId) {
        super("Currency validation is unavailable for machine " + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }
}
