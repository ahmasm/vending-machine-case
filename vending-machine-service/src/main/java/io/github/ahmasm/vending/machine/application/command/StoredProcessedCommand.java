package io.github.ahmasm.vending.machine.application.command;

import java.util.Objects;
import java.util.Optional;

public record StoredProcessedCommand(
        String requestHash, Optional<ProcessedCommandResult> result) {

    public StoredProcessedCommand {
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        result = Objects.requireNonNull(result, "result must not be null");
    }
}
