package io.github.ahmasm.vending.machine.application.port.out;

public enum CurrencyRejectionReason {
    COUNTERFEIT,
    UNREADABLE,
    UNSUPPORTED_DENOMINATION,
    UNKNOWN_REFERENCE
}
