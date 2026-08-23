package io.github.ahmasm.vending.machine.domain.machine.event;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;

public record PurchaseSessionStarted(MachineId machineId, SessionId sessionId, Instant occurredAt)
        implements VendingMachineEvent {}
