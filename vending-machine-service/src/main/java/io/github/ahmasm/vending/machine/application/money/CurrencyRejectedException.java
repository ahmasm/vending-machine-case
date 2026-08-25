package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.application.port.out.CurrencyRejectionReason;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;

public final class CurrencyRejectedException extends RuntimeException {

    private final MachineId machineId;
    private final CurrencyRejectionReason reason;

    public CurrencyRejectedException(MachineId machineId, CurrencyRejectionReason reason) {
        super("Currency was rejected by machine " + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }

    public CurrencyRejectionReason reason() {
        return reason;
    }
}
