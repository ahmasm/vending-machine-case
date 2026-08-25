package io.github.ahmasm.vending.machine.adapter.out.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ahmasm.vending.machine.application.port.out.CurrencyRejectionReason;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeterministicCurrencyValidatorSimulatorTest {

    private static final MachineId MACHINE_ID = new MachineId("VM-001");

    private final DeterministicCurrencyValidatorSimulator validator =
            new DeterministicCurrencyValidatorSimulator();

    @ParameterizedTest
    @CsvSource({
        "SIM-VALID-5, FIVE",
        "SIM-VALID-10, TEN",
        "SIM-VALID-20, TWENTY",
        "SIM-VALID-50, FIFTY"
    })
    void validReferenceReturnsAuthoritativeDenomination(
            String validatorReference, Denomination denomination) {
        assertEquals(
                new CurrencyValidation.Accepted(denomination),
                validator.validate(MACHINE_ID, validatorReference));
    }

    @Test
    void counterfeitReferenceIsRejected() {
        assertEquals(
                new CurrencyValidation.Rejected(CurrencyRejectionReason.COUNTERFEIT),
                validator.validate(MACHINE_ID, "SIM-COUNTERFEIT"));
    }

    @Test
    void unreadableReferenceIsRejected() {
        assertEquals(
                new CurrencyValidation.Rejected(CurrencyRejectionReason.UNREADABLE),
                validator.validate(MACHINE_ID, "SIM-UNREADABLE"));
    }

    @Test
    void offlineReferenceReportsUnavailable() {
        assertEquals(
                new CurrencyValidation.Unavailable(),
                validator.validate(MACHINE_ID, "SIM-OFFLINE"));
    }

    @Test
    void unsupportedDenominationReferenceIsRejected() {
        assertEquals(
                new CurrencyValidation.Rejected(
                        CurrencyRejectionReason.UNSUPPORTED_DENOMINATION),
                validator.validate(MACHINE_ID, "SIM-UNSUPPORTED"));
    }

    @Test
    void unknownReferenceIsRejected() {
        assertEquals(
                new CurrencyValidation.Rejected(CurrencyRejectionReason.UNKNOWN_REFERENCE),
                validator.validate(MACHINE_ID, "UNKNOWN"));
    }
}
