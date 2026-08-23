package io.github.ahmasm.vending.machine.domain.machine.event;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;

public record SessionExpired(
        MachineId machineId,
        SessionId sessionId,
        CashComposition returnedCash,
        Instant occurredAt)
        implements VendingMachineEvent {}
