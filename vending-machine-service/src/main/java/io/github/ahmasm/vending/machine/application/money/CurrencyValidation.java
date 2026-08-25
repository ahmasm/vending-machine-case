package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.util.Objects;

public sealed interface CurrencyValidation {

    record Accepted(Denomination denomination) implements CurrencyValidation {

        public Accepted {
            Objects.requireNonNull(denomination, "denomination must not be null");
        }
    }

    record Rejected(CurrencyRejectionReason reason) implements CurrencyValidation {

        public Rejected {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    record Unavailable() implements CurrencyValidation {}
}
