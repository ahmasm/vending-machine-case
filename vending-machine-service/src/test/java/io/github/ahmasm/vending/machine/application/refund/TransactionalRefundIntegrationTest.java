package io.github.ahmasm.vending.machine.application.refund;

import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class TransactionalRefundIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final RefundService refund;
    private final VendingMachineRepository machineRepository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    TransactionalRefundIntegrationTest(
            RefundService refund,
            VendingMachineRepository machineRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.refund = refund;
        this.machineRepository = machineRepository;
        transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void successfulCommandRefundsExactEscrowAndReplaysOneAtomicResult() throws Exception {
        var machineId = new MachineId("VM-REFUND-REPLAY");
        var sessionId = new SessionId("SES-REFUND-REPLAY");
        var key = new IdempotencyKey("KEY-REFUND-REPLAY");
        var machineCash = CashComposition.of(Map.of(FIFTY, 2));
        persistActiveMachine(machineId, sessionId, machineCash, TEN, FIVE);

        var first = refund.handle(command(machineId, sessionId, key));
        var replay = refund.handle(command(machineId, sessionId, key));

        var expectedRefund = CashComposition.of(Map.of(TEN, 1, FIVE, 1));
        assertEquals(expectedRefund, first.returnedCash());
        assertEquals(first, replay);
        assertThrows(
                IdempotencyKeyReusedException.class,
                () -> refund.handle(command(
                        machineId,
                        new SessionId("SES-REFUND-DIFFERENT"),
                        key)));

        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertTrue(restored.activeSessionId().isEmpty());
        assertEquals(machineCash, restored.availableCash());
        assertEquals(
                "REFUNDED",
                jdbcTemplate.queryForObject(
                        "select status from purchase_session where session_id = ?",
                        String.class,
                        sessionId.value()));
        assertEquals(0, count("session_tender", "session_id", sessionId.value()));
        assertEquals(1, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        assertEquals(
                "REFUND_COMPLETED",
                jdbcTemplate.queryForObject(
                        "select result_code from processed_command where machine_id = ?",
                        String.class,
                        machineId.value()));

        var storedResult = objectMapper.readTree(jdbcTemplate.queryForObject(
                "select result_payload::text from processed_command where machine_id = ?",
                String.class,
                machineId.value()));
        assertEquals(1, storedResult.path("returnedComposition").path("TEN").asInt());
        assertEquals(1, storedResult.path("returnedComposition").path("FIVE").asInt());

    }

    @Test
    void invalidSessionRollsBackClaimAndVersion() {
        var machineId = new MachineId("VM-REFUND-ROLLBACK");
        var activeSessionId = new SessionId("SES-REFUND-ACTIVE");
        var wrongSessionId = new SessionId("SES-REFUND-WRONG");
        var key = new IdempotencyKey("KEY-REFUND-ROLLBACK");
        persistActiveMachine(
                machineId,
                activeSessionId,
                CashComposition.of(Map.of(FIFTY, 2)),
                TEN);

        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> refund.handle(command(machineId, wrongSessionId, key)));

        assertEquals(0, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(activeSessionId, restored.activeSessionId().orElseThrow());
        assertEquals(
                CashComposition.of(Map.of(TEN, 1)),
                restored.snapshot().currentSession().orElseThrow().escrow());
    }

    private void persistActiveMachine(
            MachineId machineId,
            SessionId sessionId,
            CashComposition machineCash,
            Denomination... inserted) {
        var machine = new VendingMachine(machineId, machineCash, List.of());
        machine.startSession(sessionId, STARTED_AT);
        for (var denomination : inserted) {
            machine.acceptMoney(sessionId, denomination, STARTED_AT.plusSeconds(10));
        }
        machine.releaseEvents();
        transactions.executeWithoutResult(status -> machineRepository.save(machine));
    }

    private int count(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = ?",
                Integer.class,
                id);
    }

    private static RefundCommand command(
            MachineId machineId, SessionId sessionId, IdempotencyKey key) {
        return new RefundCommand(
                machineId,
                sessionId,
                key);
    }
}
