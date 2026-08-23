package io.github.ahmasm.vending.machine.application.port.in;

public sealed interface ProcessedCommandResult
        permits InsertMoneyResult, RefundResult, SelectProductResult, StartSessionResult {}
