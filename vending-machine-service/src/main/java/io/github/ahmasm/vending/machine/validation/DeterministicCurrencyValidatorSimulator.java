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
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public final class DeterministicCurrencyValidatorSimulator implements CurrencyValidator {

    private static final Pattern ACCEPTED_REFERENCE = Pattern.compile(
            "SIM-VALID-(5|10|20|50)(?:-[A-Za-z0-9][A-Za-z0-9._-]{0,63})?");

    @Override
    public CurrencyValidation validate(MachineId machineId, String validatorReference) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(validatorReference, "validatorReference must not be null");

        var acceptedReference = ACCEPTED_REFERENCE.matcher(validatorReference);
        if (acceptedReference.matches()) {
            var denomination = switch (acceptedReference.group(1)) {
                case "5" -> FIVE;
                case "10" -> TEN;
                case "20" -> TWENTY;
                case "50" -> FIFTY;
                default -> throw new IllegalStateException("Unsupported simulator denomination");
            };
            return new CurrencyValidation.Accepted(denomination);
        }

        return switch (validatorReference) {
            case "SIM-COUNTERFEIT" -> new CurrencyValidation.Rejected(COUNTERFEIT);
            case "SIM-UNREADABLE" -> new CurrencyValidation.Rejected(UNREADABLE);
            case "SIM-UNSUPPORTED" -> new CurrencyValidation.Rejected(UNSUPPORTED_DENOMINATION);
            case "SIM-OFFLINE" -> new CurrencyValidation.Unavailable();
            default -> new CurrencyValidation.Rejected(UNKNOWN_REFERENCE);
        };
    }
}
