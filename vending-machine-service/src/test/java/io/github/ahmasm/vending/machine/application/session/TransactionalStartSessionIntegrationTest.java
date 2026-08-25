package io.github.ahmasm.vending.machine.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.command.StartSessionResult;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionAlreadyExistsException;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionalStartSessionIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final StartSessionService startSession;
    private final VendingMachineRepository machineRepository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MockMvc mockMvc;

    @Autowired
    TransactionalStartSessionIntegrationTest(
            StartSessionService startSession,
            VendingMachineRepository machineRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MockMvc mockMvc) {
        this.startSession = startSession;
        this.machineRepository = machineRepository;
        transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mockMvc = mockMvc;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void successfulCommandReplaysGeneratedSessionWithoutAnotherEffect()
            throws Exception {
        var machineId = new MachineId("VM-START-REPLAY");
        var key = new IdempotencyKey("KEY-START-REPLAY");
        persistEmptyMachine(machineId);

        var first = startSession.handle(command(machineId, key));
        var replay = startSession.handle(command(machineId, key));

        assertEquals(first, replay);
        UUID.fromString(first.sessionId().value());
        var machine = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(first.sessionId(), machine.activeSessionId().orElseThrow());
        assertEquals(
                first.startedAt(),
                machine.snapshot().currentSession().orElseThrow().startedAt());
        assertEquals(1, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        assertEquals(
                "SESSION_STARTED",
                jdbcTemplate.queryForObject(
                        "select result_code from processed_command where machine_id = ?",
                        String.class,
                        machineId.value()));

        var storedResult = objectMapper.readTree(jdbcTemplate.queryForObject(
                "select result_payload::text from processed_command where machine_id = ?",
                String.class,
                machineId.value()));
        assertEquals(first.sessionId().value(), storedResult.path("sessionId").asText());
        assertEquals(first.startedAt(), Instant.parse(storedResult.path("startedAt").asText()));

    }

    @Test
    void activeSessionFailureRollsBackClaimAndVersion() {
        var machineId = new MachineId("VM-START-ROLLBACK");
        var activeSessionId = new SessionId("SES-ALREADY-ACTIVE");
        persistActiveMachine(machineId, activeSessionId);

        assertThrows(
                ActiveSessionAlreadyExistsException.class,
                () -> startSession.handle(command(
                        machineId, new IdempotencyKey("KEY-START-ROLLBACK"))));

        var machine = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(activeSessionId, machine.activeSessionId().orElseThrow());
        assertEquals(0, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
    }

    @Test
    void sessionQueryReturnsActiveStatusBalanceAndTimestamps() throws Exception {
        var machineId = new MachineId("VM-SESSION-ACTIVE");
        var sessionId = UUID.fromString("76b2144d-0d58-42b7-8452-16a64d79a5c1");
        var startedAt = Instant.parse("2026-08-23T10:00:00Z");
        var lastActivityAt = Instant.parse("2026-08-23T10:01:00Z");
        persistEmptyMachine(machineId);
        insertSession(machineId, sessionId, "ACTIVE", startedAt, lastActivityAt);
        insertTender(sessionId, 10, 1);
        insertTender(sessionId, 20, 2);

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}",
                                machineId.value(),
                                sessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.machineId").value(machineId.value()))
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance.amount").value(50))
                .andExpect(jsonPath("$.balance.currency").value("UNIT"))
                .andExpect(jsonPath("$.startedAt").value(startedAt.toString()))
                .andExpect(jsonPath("$.lastActivityAt").value(lastActivityAt.toString()));
    }

    @Test
    void sessionQueryReturnsTerminalSessionWithZeroBalance() throws Exception {
        var machineId = new MachineId("VM-SESSION-REFUNDED");
        var sessionId = UUID.fromString("bbd11f77-f9d0-478d-922c-f9596968ab9c");
        var startedAt = Instant.parse("2026-08-23T11:00:00Z");
        var lastActivityAt = Instant.parse("2026-08-23T11:01:00Z");
        persistEmptyMachine(machineId);
        insertSession(machineId, sessionId, "REFUNDED", startedAt, lastActivityAt);

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}",
                                machineId.value(),
                                sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.balance.amount").value(0))
                .andExpect(jsonPath("$.balance.currency").value("UNIT"));
    }

    @Test
    void missingSessionQueryReturnsNotFoundProblem() throws Exception {
        var machineId = new MachineId("VM-SESSION-MISSING");
        var sessionId = UUID.fromString("4529bf2d-b4df-4390-b9ee-fc19a1e2d4bb");
        persistEmptyMachine(machineId);

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}",
                                machineId.value(),
                                sessionId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-SESSION-MISSING/sessions/" + sessionId));
    }

    @Test
    void openApiPublishesCommandPathsAndImportantResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Vending Machine API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.schemas.InsertMoneyRequest.required[0]")
                        .value("validatorReference"))
                .andExpect(jsonPath("$.components.schemas.InsertMoneyRequest.properties.denomination")
                        .doesNotExist())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/products']"
                                        + ".get.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/products']"
                                        + ".get.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions']"
                                        + ".post.responses['201']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions']"
                                        + ".post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions']"
                                        + ".post.responses['500']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}']"
                                        + ".get.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}']"
                                        + ".get.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/purchases/{transactionId}']"
                                        + ".get.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/purchases/{transactionId}']"
                                        + ".get.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/money']"
                                        + ".post.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/money']"
                                        + ".post.responses['503']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/selection']"
                                        + ".post.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/selection']"
                                        + ".post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/selection']"
                                        + ".post.responses['409'].content"
                                        + "['application/problem+json'].examples")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/refund']"
                                        + ".post.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/refund']"
                                        + ".post.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/refund']"
                                        + ".post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/machines/{machineId}/sessions/{sessionId}/refund']"
                                        + ".post.responses['409'].content"
                                        + "['application/problem+json'].examples")
                        .exists());
    }

    private void persistEmptyMachine(MachineId machineId) {
        transactions.executeWithoutResult(
                status -> machineRepository.save(new VendingMachine(machineId)));
    }

    private void persistActiveMachine(MachineId machineId, SessionId sessionId) {
        var machine = new VendingMachine(machineId);
        machine.startSession(sessionId, Instant.parse("2026-08-23T10:00:00Z"));
        machine.releaseEvents();
        transactions.executeWithoutResult(status -> machineRepository.save(machine));
    }

    private void insertSession(
            MachineId machineId,
            UUID sessionId,
            String status,
            Instant startedAt,
            Instant lastActivityAt) {
        jdbcTemplate.update(
                """
                insert into purchase_session (
                    session_id,
                    machine_id,
                    status,
                    started_at,
                    last_activity_at
                ) values (?, ?, ?, ?, ?)
                """,
                sessionId.toString(),
                machineId.value(),
                status,
                startedAt.atOffset(ZoneOffset.UTC),
                lastActivityAt.atOffset(ZoneOffset.UTC));
    }

    private void insertTender(UUID sessionId, int denomination, int quantity) {
        jdbcTemplate.update(
                """
                insert into session_tender (session_id, denomination, quantity)
                values (?, ?, ?)
                """,
                sessionId.toString(),
                denomination,
                quantity);
    }

    private int count(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = ?",
                Integer.class,
                id);
    }

    private static StartSessionCommand command(MachineId machineId, IdempotencyKey key) {
        return new StartSessionCommand(
                machineId,
                key);
    }
}
