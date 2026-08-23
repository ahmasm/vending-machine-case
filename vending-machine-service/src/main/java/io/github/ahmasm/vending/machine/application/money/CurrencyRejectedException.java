package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.util.Objects;

public final class CurrencyRejectedException extends RuntimeException {

    private final MachineId machineId;
    private final Denomination denomination;

    public CurrencyRejectedException(MachineId machineId, Denomination denomination) {
        super("Currency was rejected by machine " + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        this.denomination = Objects.requireNonNull(denomination, "denomination must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }

    public Denomination denomination() {
        return denomination;
    }
}
