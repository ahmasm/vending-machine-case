package io.github.ahmasm.vending.machine.application.command;

public sealed interface ProcessedCommandResult
        permits InsertMoneyResult, RefundResult, SelectProductResult, StartSessionResult {}
