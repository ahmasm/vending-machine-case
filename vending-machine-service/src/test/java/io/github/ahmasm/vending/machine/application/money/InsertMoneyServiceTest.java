package io.github.ahmasm.vending.machine.application.money;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.command.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyRejectionReason;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InsertMoneyServiceTest {

    private static final MachineId MACHINE_ID = new MachineId("VM-001");
    private static final SessionId SESSION_ID = new SessionId("SES-001");
    private static final IdempotencyKey IDEMPOTENCY_KEY =
            new IdempotencyKey("insert-money-001");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant ACCEPTED_AT = STARTED_AT.plusSeconds(10);
    private static final InsertMoneyCommand COMMAND =
            new InsertMoneyCommand(MACHINE_ID, SESSION_ID, "SIM-VALID-10", IDEMPOTENCY_KEY);

    @Test
    void acceptedCurrencyExecutesValidatedMutationAndReturnsBalance() {
        var expected = new InsertMoneyResult(Money.of(10, UNIT));
        var executor = mock(TransactionalValidatedMoneyExecutor.class);
        when(executor.execute(eq(COMMAND), eq(TEN), anyString(), eq(ACCEPTED_AT)))
                .thenReturn(expected);
        var service = new InsertMoneyService(
                (machineId, validatorReference) -> new CurrencyValidation.Accepted(TEN),
                emptyProcessedCommandStore(),
                executor,
                fixedClock());

        var result = service.handle(COMMAND);

        assertEquals(expected, result);
        verify(executor).execute(eq(COMMAND), eq(TEN), anyString(), eq(ACCEPTED_AT));
    }

    @Test
    void rejectedCurrencyDoesNotEnterValidatedMutation() {
        var executor = mock(TransactionalValidatedMoneyExecutor.class);
        var service = new InsertMoneyService(
                (machineId, validatorReference) ->
                        new CurrencyValidation.Rejected(CurrencyRejectionReason.COUNTERFEIT),
                emptyProcessedCommandStore(),
                executor,
                fixedClock());

        assertThrows(CurrencyRejectedException.class, () -> service.handle(COMMAND));

        verify(executor, never()).execute(any(), any(), anyString(), any());
    }

    @Test
    void unavailableValidatorDoesNotEnterValidatedMutation() {
        var executor = mock(TransactionalValidatedMoneyExecutor.class);
        var service = new InsertMoneyService(
                (machineId, validatorReference) -> new CurrencyValidation.Unavailable(),
                emptyProcessedCommandStore(),
                executor,
                fixedClock());

        assertThrows(
                CurrencyValidationUnavailableException.class,
                () -> service.handle(COMMAND));

        verify(executor, never()).execute(any(), any(), anyString(), any());
    }

    private static ProcessedCommandStore emptyProcessedCommandStore() {
        var store = mock(ProcessedCommandStore.class);
        when(store.find(MACHINE_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        return store;
    }

    private static Clock fixedClock() {
        return Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC);
    }
}
