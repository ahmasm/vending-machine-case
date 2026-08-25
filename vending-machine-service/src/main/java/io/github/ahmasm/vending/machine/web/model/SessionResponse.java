package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

@Schema(name = "SessionResponse")
public record SessionResponse(
        @Schema(example = "VM-001") String machineId,
        @Schema(example = "6f9619ff-8b86-d011-b42d-00c04fc964ff") String sessionId,
        @Schema(example = "ACTIVE") String status,
        MoneyResponse balance,
        @Schema(example = "2026-08-23T10:00:00Z") Instant startedAt,
        @Schema(example = "2026-08-23T10:01:00Z") Instant lastActivityAt) {

    public SessionResponse {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
    }
}
