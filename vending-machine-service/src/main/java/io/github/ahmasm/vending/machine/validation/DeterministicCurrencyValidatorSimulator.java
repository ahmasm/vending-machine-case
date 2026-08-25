package io.github.ahmasm.vending.machine.validation;

import static io.github.ahmasm.vending.machine.application.money.CurrencyRejectionReason.COUNTERFEIT;
import static io.github.ahmasm.vending.machine.application.money.CurrencyRejectionReason.UNKNOWN_REFERENCE;
import static io.github.ahmasm.vending.machine.application.money.CurrencyRejectionReason.UNREADABLE;
import static io.github.ahmasm.vending.machine.application.money.CurrencyRejectionReason.UNSUPPORTED_DENOMINATION;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TWENTY;

import io.github.ahmasm.vending.machine.application.money.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.money.CurrencyValidator;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public final class DeterministicCurrencyValidatorSimulator implements CurrencyValidator {

    @Override
    public CurrencyValidation validate(MachineId machineId, String validatorReference) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(validatorReference, "validatorReference must not be null");

        return switch (validatorReference) {
            case "SIM-VALID-5" -> new CurrencyValidation.Accepted(FIVE);
            case "SIM-VALID-10" -> new CurrencyValidation.Accepted(TEN);
            case "SIM-VALID-20" -> new CurrencyValidation.Accepted(TWENTY);
            case "SIM-VALID-50" -> new CurrencyValidation.Accepted(FIFTY);
            case "SIM-COUNTERFEIT" -> new CurrencyValidation.Rejected(COUNTERFEIT);
            case "SIM-UNREADABLE" -> new CurrencyValidation.Rejected(UNREADABLE);
            case "SIM-UNSUPPORTED" -> new CurrencyValidation.Rejected(UNSUPPORTED_DENOMINATION);
            case "SIM-OFFLINE" -> new CurrencyValidation.Unavailable();
            default -> new CurrencyValidation.Rejected(UNKNOWN_REFERENCE);
        };
    }
}
