package io.github.ahmasm.vending.machine.adapter.out.persistence.machine;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.application.port.out.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.Slot;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.money.Money;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaVendingMachineRepository.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaVendingMachineRepositoryTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 10;

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final VendingMachineRepository repository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;
    private final SessionFactory sessionFactory;

    @Autowired
    JpaVendingMachineRepositoryTest(
            VendingMachineRepository repository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            EntityManagerFactory entityManagerFactory) {
        this.repository = repository;
        transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void savingAndLoadingRoundTripsAggregateStateWithoutDomainEvents() {
        var machineId = new MachineId("VM-ROUNDTRIP");
        var sessionId = new SessionId("SES-ROUNDTRIP");
        var machine = machine(machineId, sessionId);
        var expectedState = machine.snapshot();
        machine.releaseEvents();

        inTransaction(() -> repository.save(machine));

        var restored = inTransactionResult(() -> repository.findById(machineId).orElseThrow());

        assertEquals(expectedState, restored.snapshot());
        assertTrue(restored.releaseEvents().isEmpty());
    }

    @Test
    void mutationLoadReadsOnlyActiveSessionRegardlessOfTerminalHistory() {
        var machineId = new MachineId("VM-SESSION-HISTORY");
        var activeSessionId = new SessionId("SES-ACTIVE");
        inTransaction(() -> repository.save(new VendingMachine(machineId)));
        insertTerminalSessionHistory(machineId, 100);
        insertActiveSession(machineId, activeSessionId);
        var statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var restored = inTransactionResult(() -> {
            var machine = repository.findForMutation(machineId).orElseThrow();
            machine.acceptMoney(activeSessionId, TEN, STARTED_AT.plusSeconds(20));
            repository.save(machine);
            return machine;
        });
        var activeSession = restored.snapshot().currentSession().orElseThrow();

        assertEquals(activeSessionId, activeSession.id());
        assertEquals(2, activeSession.escrow().quantityOf(TEN));
        assertEquals(
                101,
                jdbcTemplate.queryForObject(
                        "select count(*) from purchase_session where machine_id = ?",
                        Integer.class,
                        machineId.value()));
        assertEquals(
                1L,
                statistics
                        .getEntityStatistics(PurchaseSessionJpaEntity.class.getName())
                        .getLoadCount());
    }

    @Test
    void concurrentChildMutationsConflictThroughForcedRootVersionIncrement() throws Exception {
        var machineId = new MachineId("VM-CONCURRENT");
        var sessionId = new SessionId("SES-CONCURRENT");
        var machine = machine(machineId, sessionId);
        inTransaction(() -> repository.save(machine));
        var loadedTogether = new CyclicBarrier(2);

        Throwable firstFailure;
        Throwable secondFailure;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> mutateTender(machineId, sessionId, loadedTogether));
            var second = workers.submit(() -> mutateTender(machineId, sessionId, loadedTogether));
            firstFailure = first.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            secondFailure = second.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        var failures = java.util.stream.Stream.of(firstFailure, secondFailure)
                .filter(failure -> failure != null)
                .toList();
        assertEquals(1, failures.size());
        assertTrue(isOptimisticLockFailure(failures.getFirst()));

        var restored = inTransactionResult(() -> repository.findById(machineId).orElseThrow());
        var activeSession = restored.snapshot().currentSession().orElseThrow();
        assertEquals(2, activeSession.escrow().quantityOf(TEN));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
    }

    private Throwable mutateTender(
            MachineId machineId, SessionId sessionId, CyclicBarrier loadedTogether) {
        try {
            transactions.executeWithoutResult(status -> {
                var machine = repository.findForMutation(machineId).orElseThrow();
                await(loadedTogether);
                machine.acceptMoney(sessionId, TEN, STARTED_AT.plusSeconds(20));
                repository.save(machine);
            });
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static VendingMachine machine(MachineId machineId, SessionId sessionId) {
        var product = new ProductSnapshot(
                new ProductId("COLA"), "Cola", Money.of(35, UNIT));
        var machine = new VendingMachine(
                machineId,
                CashComposition.of(Map.of(FIVE, 2, TEN, 3)),
                List.of(new Slot(new SlotCode("A1"), product, 4)));
        machine.startSession(sessionId, STARTED_AT);
        machine.acceptMoney(sessionId, TEN, STARTED_AT.plusSeconds(10));
        return machine;
    }

    private void insertTerminalSessionHistory(MachineId machineId, int sessionCount) {
        jdbcTemplate.update(
                """
                insert into purchase_session (
                    session_id,
                    machine_id,
                    status,
                    started_at,
                    last_activity_at
                )
                select
                    'SES-HISTORY-' || sequence_number,
                    ?,
                    'COMPLETED',
                    ?,
                    ?
                from generate_series(1, ?) as sequence_number
                """,
                machineId.value(),
                STARTED_AT.atOffset(ZoneOffset.UTC),
                STARTED_AT.plusSeconds(1).atOffset(ZoneOffset.UTC),
                sessionCount);
    }

    private void insertActiveSession(MachineId machineId, SessionId sessionId) {
        jdbcTemplate.update(
                """
                insert into purchase_session (
                    session_id,
                    machine_id,
                    status,
                    started_at,
                    last_activity_at
                ) values (?, ?, 'ACTIVE', ?, ?)
                """,
                sessionId.value(),
                machineId.value(),
                STARTED_AT.atOffset(ZoneOffset.UTC),
                STARTED_AT.plusSeconds(10).atOffset(ZoneOffset.UTC));
        jdbcTemplate.update(
                """
                insert into session_tender (session_id, denomination, quantity)
                values (?, ?, ?)
                """,
                sessionId.value(),
                TEN.value().amount(),
                1);
    }

    private void inTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not synchronize concurrent transactions", exception);
        }
    }

    private static boolean isOptimisticLockFailure(Throwable failure) {
        assertNotNull(failure);
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException
                    || cause instanceof OptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }
}
