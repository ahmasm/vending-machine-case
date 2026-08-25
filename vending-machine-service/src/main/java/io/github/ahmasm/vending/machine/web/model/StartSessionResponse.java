package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

@Schema(name = "StartSessionResponse")
public record StartSessionResponse(
        @Schema(example = "6f9619ff-8b86-d011-b42d-00c04fc964ff") String sessionId,
        @Schema(example = "2026-08-23T10:00:00Z") Instant startedAt) {

    public StartSessionResponse {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }
}
