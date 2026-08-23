package io.github.ahmasm.vending.machine.adapter.out.validation;

import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidator;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DeterministicCurrencyValidator implements CurrencyValidator {

    @Override
    public CurrencyValidation validate(MachineId machineId, Denomination denomination) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(denomination, "denomination must not be null");
        return CurrencyValidation.ACCEPTED;
    }
}
