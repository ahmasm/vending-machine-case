package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public final class CurrencyAcceptanceAlreadyConsumedException extends RuntimeException {

    public CurrencyAcceptanceAlreadyConsumedException(MachineId machineId) {
        super("Currency acceptance was already consumed for machine "
                + Objects.requireNonNull(machineId, "machineId must not be null").value());
    }
}
