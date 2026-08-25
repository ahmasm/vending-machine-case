package io.github.ahmasm.vending.machine.application.command;

import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;
import java.util.Objects;

public record StartSessionResult(SessionId sessionId, Instant startedAt)
        implements ProcessedCommandResult {

    public StartSessionResult {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }
}
