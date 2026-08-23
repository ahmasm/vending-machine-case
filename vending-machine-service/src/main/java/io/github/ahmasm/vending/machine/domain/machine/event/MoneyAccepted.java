package io.github.ahmasm.vending.machine.domain.machine.event;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;

public record MoneyAccepted(
        MachineId machineId,
        SessionId sessionId,
        Denomination denomination,
        Money balance,
        Instant occurredAt)
        implements VendingMachineEvent {}
