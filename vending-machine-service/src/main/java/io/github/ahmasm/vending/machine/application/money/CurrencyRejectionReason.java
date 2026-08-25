package io.github.ahmasm.vending.machine.application.money;

public enum CurrencyRejectionReason {
    COUNTERFEIT,
    UNREADABLE,
    UNSUPPORTED_DENOMINATION,
    UNKNOWN_REFERENCE
}
