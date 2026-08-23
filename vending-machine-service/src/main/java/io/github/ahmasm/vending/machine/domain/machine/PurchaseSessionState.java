package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import java.time.Instant;
import java.util.Objects;

public record PurchaseSessionState(
        SessionId id,
        SessionStatus status,
        CashComposition escrow,
        Instant startedAt,
        Instant lastActivityAt) {

    public PurchaseSessionState {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(escrow, "escrow must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
        if (status != SessionStatus.ACTIVE && !escrow.isEmpty()) {
            throw new IllegalArgumentException("Terminal session escrow must be empty");
        }
    }
}
