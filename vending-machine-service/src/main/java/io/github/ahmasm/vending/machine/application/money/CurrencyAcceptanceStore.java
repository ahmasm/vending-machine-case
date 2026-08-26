package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.time.Instant;

public interface CurrencyAcceptanceStore {

    boolean claim(MachineId machineId, String validatorReference, Instant consumedAt);
}
