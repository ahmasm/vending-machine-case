package io.github.ahmasm.vending.machine.application.port.in;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.util.Objects;

public record InsertMoneyCommand(
        MachineId machineId,
        SessionId sessionId,
        Denomination denomination,
        IdempotencyKey idempotencyKey) {

    public InsertMoneyCommand {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(denomination, "denomination must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
