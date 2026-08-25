package io.github.ahmasm.vending.machine.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ahmasm.vending.machine.application.money.CurrencyValidation;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import org.junit.jupiter.api.Test;

class FailClosedCurrencyValidatorTest {

    @Test
    void missingHardwareIntegrationNeverAcceptsCurrency() {
        var validator = new FailClosedCurrencyValidator();

        assertEquals(
                new CurrencyValidation.Unavailable(),
                validator.validate(new MachineId("VM-001"), "UNTRUSTED-REFERENCE"));
    }
}
