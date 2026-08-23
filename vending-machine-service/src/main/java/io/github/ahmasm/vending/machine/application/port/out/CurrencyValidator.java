package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;

@FunctionalInterface
public interface CurrencyValidator {

    CurrencyValidation validate(MachineId machineId, Denomination denomination);
}
