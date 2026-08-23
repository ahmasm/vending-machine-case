package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

final class PurchaseSession {

    private final SessionId id;
    private final Instant startedAt;
    private SessionStatus status;
    private CashComposition escrow;
    private Instant lastActivityAt;

    private PurchaseSession(
            SessionId id,
            SessionStatus status,
            CashComposition escrow,
            Instant startedAt,
            Instant lastActivityAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.escrow = Objects.requireNonNull(escrow, "escrow must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.lastActivityAt =
                Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
    }

    static PurchaseSession start(SessionId id, Instant startedAt) {
        return new PurchaseSession(
                id,
                SessionStatus.ACTIVE,
                CashComposition.empty(),
                startedAt,
                startedAt);
    }

    static PurchaseSession restore(PurchaseSessionState state) {
        Objects.requireNonNull(state, "state must not be null");
        return new PurchaseSession(
                state.id(),
                state.status(),
                state.escrow(),
                state.startedAt(),
                state.lastActivityAt());
    }

    SessionId id() {
        return id;
    }

    boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    PurchaseSessionState state() {
        return new PurchaseSessionState(id, status, escrow, startedAt, lastActivityAt);
    }

    Money acceptMoney(Denomination denomination, Instant acceptedAt) {
        requireActive();
        Objects.requireNonNull(denomination, "denomination must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        escrow = escrow.add(denomination);
        lastActivityAt = acceptedAt;
        return escrow.total();
    }

    Instant expiresAt(Duration inactivityTimeout) {
        requireActive();
        Objects.requireNonNull(inactivityTimeout, "inactivityTimeout must not be null");
        return lastActivityAt.plus(inactivityTimeout);
    }

    CashComposition escrow() {
        requireActive();
        return escrow;
    }

    void complete() {
        requireActive();
        escrow = CashComposition.empty();
        status = SessionStatus.COMPLETED;
    }

    CashComposition refund() {
        requireActive();
        var returnedCash = escrow;
        escrow = CashComposition.empty();
        status = SessionStatus.REFUNDED;
        return returnedCash;
    }

    CashComposition expire() {
        requireActive();
        var returnedCash = escrow;
        escrow = CashComposition.empty();
        status = SessionStatus.EXPIRED;
        return returnedCash;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Session " + id.value() + " is terminal");
        }
    }

}
