package io.github.ahmasm.vending.machine.domain.machine.event;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;

public sealed interface VendingMachineEvent
        permits MoneyAccepted,
                PurchaseCompleted,
                PurchaseSessionStarted,
                RefundCompleted,
                SessionExpired {

    MachineId machineId();

    SessionId sessionId();

    Instant occurredAt();
}
