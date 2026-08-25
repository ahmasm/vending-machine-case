package io.github.ahmasm.vending.machine.application.session;

import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.domain.machine.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SessionNotExpiredException;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "vending.session-expiry.enabled=true",
            "vending.session-expiry.initial-delay=1h"
        })
@Import(SessionExpiryRecoveryIntegrationTest.FixedClockConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionExpiryRecoveryIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-08-23T10:03:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(2);

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final SessionExpiryRecovery recovery;
    private final TransactionalSessionExpiryExecutor expiryExecutor;
    private final SessionExpiryCandidateFinder candidateFinder;
    private final VendingMachineRepository machineRepository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SessionExpiryRecoveryIntegrationTest(
            SessionExpiryRecovery recovery,
            TransactionalSessionExpiryExecutor expiryExecutor,
            SessionExpiryCandidateFinder candidateFinder,
            VendingMachineRepository machineRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        this.recovery = recovery;
        this.expiryExecutor = expiryExecutor;
        this.candidateFinder = candidateFinder;
        this.machineRepository = machineRepository;
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
    void expiredSessionRecoveryPersistsExactRefundAcrossRepeatedRuns()
            throws Exception {
        var machineId = new MachineId("VM-EXPIRY-RECOVERY");
        var sessionId = new SessionId("SES-EXPIRY-RECOVERY");
        var machineCash = CashComposition.of(Map.of(FIFTY, 2));
        var lastActivityAt = NOW.minus(INACTIVITY_TIMEOUT);
        persistActiveMachine(
                machineId, sessionId, machineCash, lastActivityAt, TEN, FIVE);

        assertEquals(1, recovery.runOnce());
        assertEquals(0, recovery.runOnce());

        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertTrue(restored.activeSessionId().isEmpty());
        assertEquals(machineCash, restored.availableCash());
        assertEquals(
                "EXPIRED",
                jdbcTemplate.queryForObject(
                        "select status from purchase_session where session_id = ?",
                        String.class,
                        sessionId.value()));
        assertEquals(0, count("session_tender", "session_id", sessionId.value()));
        assertEquals(0, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));

    }

    @Test
    void candidateReceivingRecentActivityIsRevalidatedAndLeftActive() {
        var machineId = new MachineId("VM-EXPIRY-STALE");
        var sessionId = new SessionId("SES-EXPIRY-STALE");
        persistActiveMachine(
                machineId,
                sessionId,
                CashComposition.empty(),
                NOW.minus(INACTIVITY_TIMEOUT).minusSeconds(1),
                TEN);
        var candidate = candidateFinder
                .findCandidates(NOW.minus(INACTIVITY_TIMEOUT), 100)
                .stream()
                .filter(found -> found.machineId().equals(machineId))
                .findFirst()
                .orElseThrow();
        recordRecentActivity(machineId, sessionId);

        assertThrows(
                SessionNotExpiredException.class,
                () -> expiryExecutor.expire(candidate, NOW, INACTIVITY_TIMEOUT));

        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(sessionId, restored.activeSessionId().orElseThrow());
        assertEquals(
                CashComposition.of(Map.of(TEN, 1, FIVE, 1)),
                restored.snapshot().currentSession().orElseThrow().escrow());
        assertEquals(
                "ACTIVE",
                jdbcTemplate.queryForObject(
                        "select status from purchase_session where session_id = ?",
                        String.class,
                        sessionId.value()));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        assertTrue(candidateFinder
                .findCandidates(NOW.minus(INACTIVITY_TIMEOUT), 100)
                .stream()
                .noneMatch(found -> found.machineId().equals(machineId)));
    }

    private void persistActiveMachine(
            MachineId machineId,
            SessionId sessionId,
            CashComposition machineCash,
            Instant lastActivityAt,
            Denomination... inserted) {
        var machine = new VendingMachine(machineId, machineCash, List.of());
        machine.startSession(sessionId, lastActivityAt.minusSeconds(30));
        for (var denomination : inserted) {
            machine.acceptMoney(sessionId, denomination, lastActivityAt);
        }
        machine.releaseEvents();
        transactions.executeWithoutResult(status -> machineRepository.save(machine));
    }

    private void recordRecentActivity(MachineId machineId, SessionId sessionId) {
        transactions.executeWithoutResult(status -> {
            var machine = machineRepository.findForMutation(machineId).orElseThrow();
            machine.acceptMoney(sessionId, FIVE, NOW.minusSeconds(30));
            machineRepository.save(machine);
            machine.releaseEvents();
        });
    }

    private int count(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = ?",
                Integer.class,
                id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }
    }
}
