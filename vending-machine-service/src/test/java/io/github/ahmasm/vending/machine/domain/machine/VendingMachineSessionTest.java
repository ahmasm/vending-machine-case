package io.github.ahmasm.vending.machine.domain.machine;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.event.MoneyAccepted;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseSessionStarted;
import io.github.ahmasm.vending.machine.domain.machine.event.RefundCompleted;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VendingMachineSessionTest {

    private static final MachineId MACHINE_ID = new MachineId("VM-001");
    private static final SessionId FIRST_SESSION = new SessionId("SES-001");
    private static final SessionId SECOND_SESSION = new SessionId("SES-002");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    void startingSessionMakesItActiveEmitsEventAndRejectsConcurrentSession() {
        var machine = new VendingMachine(MACHINE_ID);

        machine.startSession(FIRST_SESSION, STARTED_AT);

        assertEquals(Optional.of(FIRST_SESSION), machine.activeSessionId());
        assertEquals(
                List.of(new PurchaseSessionStarted(MACHINE_ID, FIRST_SESSION, STARTED_AT)),
                machine.releaseEvents());

        var exception = assertThrows(
                ActiveSessionAlreadyExistsException.class,
                () -> machine.startSession(SECOND_SESSION, STARTED_AT.plusSeconds(1)));

        assertEquals(FIRST_SESSION, exception.activeSessionId());
        assertTrue(machine.releaseEvents().isEmpty());
    }

    @Test
    void acceptingMoneyAccumulatesEscrowBalanceAndEmitsEvents() {
        var machine = machineWithActiveSession();
        var firstAcceptanceAt = STARTED_AT.plusSeconds(10);
        var secondAcceptanceAt = STARTED_AT.plusSeconds(20);

        var firstBalance = machine.acceptMoney(FIRST_SESSION, FIVE, firstAcceptanceAt);
        var secondBalance = machine.acceptMoney(FIRST_SESSION, TEN, secondAcceptanceAt);

        assertEquals(Money.of(5, UNIT), firstBalance);
        assertEquals(Money.of(15, UNIT), secondBalance);
        assertEquals(
                List.of(
                        new MoneyAccepted(
                                MACHINE_ID,
                                FIRST_SESSION,
                                FIVE,
                                Money.of(5, UNIT),
                                firstAcceptanceAt),
                        new MoneyAccepted(
                                MACHINE_ID,
                                FIRST_SESSION,
                                TEN,
                                Money.of(15, UNIT),
                                secondAcceptanceAt)),
                machine.releaseEvents());
    }

    @Test
    void acceptingMoneyForDifferentSessionIsRejectedWithoutStateOrEvent() {
        var machine = machineWithActiveSession();

        var exception = assertThrows(
                ActiveSessionNotFoundException.class,
                () -> machine.acceptMoney(SECOND_SESSION, TEN, STARTED_AT.plusSeconds(10)));

        assertEquals(SECOND_SESSION, exception.requestedSessionId());
        assertTrue(machine.releaseEvents().isEmpty());
        assertEquals(CashComposition.empty(), machine.refund(FIRST_SESSION, STARTED_AT.plusSeconds(20)));
    }

    @Test
    void refundReturnsExactEscrowTerminatesSessionAndAllowsReplacementSession() {
        var machine = machineWithActiveSession();
        machine.acceptMoney(FIRST_SESSION, FIVE, STARTED_AT.plusSeconds(10));
        machine.acceptMoney(FIRST_SESSION, FIVE, STARTED_AT.plusSeconds(20));
        machine.acceptMoney(FIRST_SESSION, TEN, STARTED_AT.plusSeconds(30));
        machine.releaseEvents();
        var refundedAt = STARTED_AT.plusSeconds(40);

        var returnedCash = machine.refund(FIRST_SESSION, refundedAt);

        assertEquals(CashComposition.of(Map.of(FIVE, 2, TEN, 1)), returnedCash);
        assertEquals(Optional.empty(), machine.activeSessionId());
        assertEquals(
                List.of(new RefundCompleted(MACHINE_ID, FIRST_SESSION, returnedCash, refundedAt)),
                machine.releaseEvents());
        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> machine.acceptMoney(FIRST_SESSION, FIVE, refundedAt.plusSeconds(1)));
        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> machine.refund(FIRST_SESSION, refundedAt.plusSeconds(2)));
        assertTrue(machine.releaseEvents().isEmpty());

        machine.startSession(SECOND_SESSION, refundedAt.plusSeconds(3));

        assertEquals(Optional.of(SECOND_SESSION), machine.activeSessionId());
    }

    private static VendingMachine machineWithActiveSession() {
        var machine = new VendingMachine(MACHINE_ID);
        machine.startSession(FIRST_SESSION, STARTED_AT);
        machine.releaseEvents();
        return machine;
    }
}
