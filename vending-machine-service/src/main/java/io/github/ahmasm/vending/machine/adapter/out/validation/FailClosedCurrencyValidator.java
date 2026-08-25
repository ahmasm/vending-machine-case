package io.github.ahmasm.vending.machine.adapter.out.validation;

import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidator;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public final class FailClosedCurrencyValidator implements CurrencyValidator {

    @Override
    public CurrencyValidation validate(MachineId machineId, String validatorReference) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(validatorReference, "validatorReference must not be null");
        return new CurrencyValidation.Unavailable();
    }
}
