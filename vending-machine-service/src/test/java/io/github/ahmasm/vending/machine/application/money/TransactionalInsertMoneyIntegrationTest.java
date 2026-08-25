package io.github.ahmasm.vending.machine.application.money;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.port.in.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidator;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.application.port.out.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionalInsertMoneyIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-23T10:00:10Z");
    private static final Clock CLOCK = Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC);
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 10;

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final VendingMachineRepository machineRepository;
    private final ProcessedCommandStore processedCommandStore;
    private final TransactionalValidatedMoneyExecutor validatedMoneyExecutor;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionalInsertMoneyIntegrationTest(
            VendingMachineRepository machineRepository,
            ProcessedCommandStore processedCommandStore,
            TransactionalValidatedMoneyExecutor validatedMoneyExecutor,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        this.machineRepository = machineRepository;
        this.processedCommandStore = processedCommandStore;
        this.validatedMoneyExecutor = validatedMoneyExecutor;
        transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void successfulCommandReplaysStableResultAndRejectsKeyReuseWithoutAnotherEffect()
            throws Exception {
        var machineId = new MachineId("VM-REPLAY");
        var sessionId = new SessionId("SES-REPLAY");
        var key = new IdempotencyKey("KEY-REPLAY");
        persistActiveMachine(machineId, sessionId);
        var validations = new AtomicInteger();
        var handler = handler((ignoredMachineId, ignoredReference) -> {
            validations.incrementAndGet();
            return new CurrencyValidation.Accepted(TEN);
        });

        var first = handler.handle(command(machineId, sessionId, "SIM-VALID-10", key));
        var replay = handler.handle(command(machineId, sessionId, "SIM-VALID-10", key));

        assertEquals(new InsertMoneyResult(Money.of(10, UNIT)), first);
        assertEquals(first, replay);
        assertEquals(1, validations.get());
        assertThrows(
                IdempotencyKeyReusedException.class,
                () -> handler.handle(command(machineId, sessionId, "SIM-VALID-5", key)));
        assertEquals(1, validations.get());
        assertPersistedEffect(machineId, 1);
    }

    @Test
    void concurrentSameKeyCommandsProduceOneMutationAndOneResult()
            throws Exception {
        var machineId = new MachineId("VM-CONCURRENT-COMMAND");
        var sessionId = new SessionId("SES-CONCURRENT-COMMAND");
        var key = new IdempotencyKey("KEY-CONCURRENT-COMMAND");
        persistActiveMachine(machineId, sessionId);
        var validatorsTogether = new CyclicBarrier(2);
        var validations = new AtomicInteger();
        var handler = handler((ignoredMachineId, ignoredReference) -> {
            validations.incrementAndGet();
            await(validatorsTogether);
            return new CurrencyValidation.Accepted(TEN);
        });

        InsertMoneyResult first;
        InsertMoneyResult second;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstCall = workers.submit(
                    () -> handler.handle(command(
                            machineId, sessionId, "SIM-VALID-10", key)));
            var secondCall = workers.submit(
                    () -> handler.handle(command(
                            machineId, sessionId, "SIM-VALID-10", key)));
            first = firstCall.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second = secondCall.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertEquals(new InsertMoneyResult(Money.of(10, UNIT)), first);
        assertEquals(first, second);
        assertEquals(2, validations.get());
        assertPersistedEffect(machineId, 1);
    }

    @Test
    void domainFailureRollsBackClaimAndVersionAndLeavesKeyReusable() {
        var machineId = new MachineId("VM-ROLLBACK");
        var sessionId = new SessionId("SES-ROLLBACK");
        var key = new IdempotencyKey("KEY-ROLLBACK");
        persistActiveMachine(machineId, sessionId);
        var handler = handler((ignoredMachineId, ignoredReference) ->
                new CurrencyValidation.Accepted(TEN));

        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> handler.handle(command(
                        machineId, new SessionId("SES-WRONG"), "SIM-VALID-10", key)));

        assertPersistedEffect(machineId, 0);
        var retry = handler.handle(command(machineId, sessionId, "SIM-VALID-10", key));
        assertEquals(new InsertMoneyResult(Money.of(10, UNIT)), retry);
        assertPersistedEffect(machineId, 1);
    }

    private InsertMoneyService handler(CurrencyValidator validator) {
        return new InsertMoneyService(
                validator, processedCommandStore, validatedMoneyExecutor, CLOCK);
    }

    private void persistActiveMachine(MachineId machineId, SessionId sessionId) {
        var machine = new VendingMachine(machineId);
        machine.startSession(sessionId, ACCEPTED_AT.minusSeconds(10));
        machine.releaseEvents();
        transactions.executeWithoutResult(status -> machineRepository.save(machine));
    }

    private void assertPersistedEffect(MachineId machineId, int expectedEffects) {
        var machine = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        var quantity = machine.snapshot()
                .currentSession()
                .orElseThrow()
                .escrow()
                .quantityOf(TEN);
        assertEquals(expectedEffects, quantity);
        assertEquals(
                expectedEffects,
                count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                (long) expectedEffects,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
    }

    private int count(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = ?",
                Integer.class,
                id);
    }

    private static InsertMoneyCommand command(
            MachineId machineId,
            SessionId sessionId,
            String validatorReference,
            IdempotencyKey key) {
        return new InsertMoneyCommand(
                machineId,
                sessionId,
                validatorReference,
                key);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not synchronize currency validation", exception);
        }
    }
}
