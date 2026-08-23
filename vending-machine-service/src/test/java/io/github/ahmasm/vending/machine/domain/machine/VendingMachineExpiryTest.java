package io.github.ahmasm.vending.machine.domain.machine;

import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.event.SessionExpired;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VendingMachineExpiryTest {

    private static final MachineId MACHINE_ID = new MachineId("VM-001");
    private static final SessionId SESSION_ID = new SessionId("SES-001");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(2);

    @Test
    void expiringAtTheInactivityBoundaryReturnsEscrowAndTerminatesSession() {
        var lastActivityAt = STARTED_AT.plusSeconds(30);
        var expiredAt = lastActivityAt.plus(INACTIVITY_TIMEOUT);
        var initialCash = CashComposition.of(Map.of(TEN, 2));
        var machine = machine(initialCash);
        machine.startSession(SESSION_ID, STARTED_AT);
        machine.acceptMoney(SESSION_ID, FIVE, lastActivityAt);
        machine.acceptMoney(SESSION_ID, TEN, lastActivityAt);
        machine.releaseEvents();

        var returnedCash = machine.expireSession(SESSION_ID, expiredAt, INACTIVITY_TIMEOUT);

        assertEquals(CashComposition.of(Map.of(FIVE, 1, TEN, 1)), returnedCash);
        assertEquals(initialCash, machine.availableCash());
        assertEquals(Optional.empty(), machine.activeSessionId());
        assertEquals(
                List.of(new SessionExpired(MACHINE_ID, SESSION_ID, returnedCash, expiredAt)),
                machine.releaseEvents());
        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> machine.refund(SESSION_ID, expiredAt.plusSeconds(1)));
        assertTrue(machine.releaseEvents().isEmpty());
    }

    @Test
    void acceptingMoneyPostponesExpiryWithoutMutatingStateOnEarlyRecovery() {
        var acceptedAt = STARTED_AT.plusSeconds(90);
        var checkedAt = STARTED_AT.plus(INACTIVITY_TIMEOUT).plusSeconds(1);
        var expectedExpiryAt = acceptedAt.plus(INACTIVITY_TIMEOUT);
        var machine = machine(CashComposition.empty());
        machine.startSession(SESSION_ID, STARTED_AT);
        machine.acceptMoney(SESSION_ID, TEN, acceptedAt);
        machine.releaseEvents();

        var exception = assertThrows(
                SessionNotExpiredException.class,
                () -> machine.expireSession(SESSION_ID, checkedAt, INACTIVITY_TIMEOUT));

        assertEquals(expectedExpiryAt, exception.expiresAt());
        assertEquals(checkedAt, exception.checkedAt());
        assertEquals(Optional.of(SESSION_ID), machine.activeSessionId());
        assertEquals(CashComposition.empty(), machine.availableCash());
        assertTrue(machine.releaseEvents().isEmpty());
        assertEquals(
                CashComposition.of(Map.of(TEN, 1)),
                machine.refund(SESSION_ID, checkedAt));
    }

    private static VendingMachine machine(CashComposition initialCash) {
        return new VendingMachine(MACHINE_ID, initialCash, List.of());
    }
}
