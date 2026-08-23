package io.github.ahmasm.vending.machine.domain.machine;

import java.time.Instant;
import java.util.Objects;

public final class SessionNotExpiredException extends IllegalStateException {

    private final Instant expiresAt;
    private final Instant checkedAt;

    public SessionNotExpiredException(Instant expiresAt, Instant checkedAt) {
        super("Session remains active until " + expiresAt);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant checkedAt() {
        return checkedAt;
    }
}
