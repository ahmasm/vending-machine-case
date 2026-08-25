package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.command.ProcessedCommandResult;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.time.Instant;
import java.util.Optional;

public interface ProcessedCommandStore {

    Optional<StoredProcessedCommand> find(MachineId machineId, IdempotencyKey idempotencyKey);

    boolean claim(
            MachineId machineId, IdempotencyKey idempotencyKey, String requestHash);

    void complete(
            MachineId machineId,
            IdempotencyKey idempotencyKey,
            String requestHash,
            ProcessedCommandResult result,
            Instant completedAt);
}
