package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
@FunctionalInterface
public interface CurrencyValidator {

    CurrencyValidation validate(MachineId machineId, String validatorReference);
}
