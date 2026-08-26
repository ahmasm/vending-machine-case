package io.github.ahmasm.vending.machine.application.purchase;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.command.SelectProductResult;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyService;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.InsufficientBalanceException;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.Slot;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseCompleted;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("demo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TransactionalSelectProductIntegrationTest.FailingPurchaseListenerConfiguration.class)
class TransactionalSelectProductIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final SlotCode SLOT_CODE = new SlotCode("A1");
    private static final ProductId PRODUCT_ID = new ProductId("COLA");
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 10;

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final SelectProductService selectProduct;
    private final InsertMoneyService insertMoney;
    private final VendingMachineRepository machineRepository;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;
    private final MockMvc mockMvc;

    @Autowired
    TransactionalSelectProductIntegrationTest(
            SelectProductService selectProduct,
            InsertMoneyService insertMoney,
            VendingMachineRepository machineRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            MockMvc mockMvc) {
        this.selectProduct = selectProduct;
        this.insertMoney = insertMoney;
        this.machineRepository = machineRepository;
        transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
        this.mockMvc = mockMvc;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void successfulCommandCommitsPurchaseAndReplaysOneStableResult() throws Exception {
        var machineId = new MachineId("VM-PURCHASE-REPLAY");
        var sessionId = new SessionId("SES-PURCHASE-REPLAY");
        var key = new IdempotencyKey("KEY-PURCHASE-REPLAY");
        persistMachine(machineId, sessionId, productPricedAt(35), 2, FIFTY);

        var first = selectProduct.handle(command(machineId, sessionId, SLOT_CODE, key));
        var replay = selectProduct.handle(command(machineId, sessionId, SLOT_CODE, key));

        assertEquals(first, replay);
        UUID.fromString(first.purchase().transactionId().value());
        assertEquals(machineId, first.purchase().machineId());
        assertEquals(sessionId, first.purchase().sessionId());
        assertEquals(SLOT_CODE, first.purchase().slotCode());
        assertEquals(PRODUCT_ID, first.purchase().product().id());
        assertEquals(Money.of(35, UNIT), first.purchase().product().price());
        assertEquals(Money.of(50, UNIT), first.purchase().insertedAmount());
        assertEquals(CashComposition.of(Map.of(TEN, 1, FIVE, 1)), first.purchase().change());

        assertThrows(
                IdempotencyKeyReusedException.class,
                () -> selectProduct.handle(command(
                        machineId,
                        sessionId,
                        new SlotCode("A2"),
                        key)));

        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(1, restored.stockOf(SLOT_CODE));
        assertEquals(CashComposition.of(Map.of(FIFTY, 1)), restored.availableCash());
        assertTrue(restored.activeSessionId().isEmpty());
        assertEquals(1, count("purchase", "machine_id", machineId.value()));
        assertEquals(2, count("purchase_change", "transaction_id", first.purchase().transactionId().value()));
        assertEquals(1, count("processed_command", "machine_id", machineId.value()));
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        assertEquals(
                "PURCHASE_COMPLETED",
                jdbcTemplate.queryForObject(
                        "select result_code from processed_command where machine_id = ?",
                        String.class,
                        machineId.value()));
        assertEquals(
                "COMPLETED",
                jdbcTemplate.queryForObject(
                        "select status from purchase_session where session_id = ?",
                        String.class,
                        sessionId.value()));

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/purchases/{transactionId}",
                                machineId.value(),
                                first.purchase().transactionId().value()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId")
                        .value(first.purchase().transactionId().value()))
                .andExpect(jsonPath("$.machineId").value(machineId.value()))
                .andExpect(jsonPath("$.sessionId").value(sessionId.value()))
                .andExpect(jsonPath("$.slotCode").value(SLOT_CODE.value()))
                .andExpect(jsonPath("$.product.id").value(PRODUCT_ID.value()))
                .andExpect(jsonPath("$.product.name").value("Cola"))
                .andExpect(jsonPath("$.price.amount").value(35))
                .andExpect(jsonPath("$.price.currency").value("UNIT"))
                .andExpect(jsonPath("$.insertedAmount.amount").value(50))
                .andExpect(jsonPath("$.insertedAmount.currency").value("UNIT"))
                .andExpect(jsonPath("$.change.total.amount").value(15))
                .andExpect(jsonPath("$.change.total.currency").value("UNIT"))
                .andExpect(jsonPath("$['change']['composition']['10']").value(1))
                .andExpect(jsonPath("$['change']['composition']['5']").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

    }

    @Test
    void purchaseQueryDoesNotExposeAnotherMachinesPurchase() throws Exception {
        var ownerMachineId = new MachineId("VM-PURCHASE-OWNER");
        var ownerSessionId = new SessionId("SES-PURCHASE-OWNER");
        persistMachine(ownerMachineId, ownerSessionId, productPricedAt(35), 1, FIFTY);
        var purchase = selectProduct.handle(command(
                        ownerMachineId,
                        ownerSessionId,
                        SLOT_CODE,
                        new IdempotencyKey("KEY-PURCHASE-OWNER")))
                .purchase();

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/purchases/{transactionId}",
                                "VM-PURCHASE-OTHER",
                                purchase.transactionId().value()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-PURCHASE-OTHER/purchases/"
                                + purchase.transactionId().value()));
    }

    @Test
    void exactPaymentPurchaseQueryReturnsEmptyChangeComposition() throws Exception {
        var machineId = new MachineId("VM-PURCHASE-EXACT");
        var sessionId = new SessionId("SES-PURCHASE-EXACT");
        persistMachine(machineId, sessionId, productPricedAt(50), 1, FIFTY);
        var purchase = selectProduct.handle(command(
                        machineId,
                        sessionId,
                        SLOT_CODE,
                        new IdempotencyKey("KEY-PURCHASE-EXACT")))
                .purchase();

        mockMvc.perform(get(
                                "/api/v1/machines/{machineId}/purchases/{transactionId}",
                                machineId.value(),
                                purchase.transactionId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change.total.amount").value(0))
                .andExpect(jsonPath("$.change.total.currency").value("UNIT"))
                .andExpect(jsonPath("$.change.composition").isEmpty());
    }

    @Test
    void failedPurchaseRollsBackClaimAndAllowsSameKeyAfterBalanceChanges() {
        var machineId = new MachineId("VM-PURCHASE-ROLLBACK");
        var sessionId = new SessionId("SES-PURCHASE-ROLLBACK");
        var selectionKey = new IdempotencyKey("KEY-PURCHASE-ROLLBACK");
        persistMachine(machineId, sessionId, productPricedAt(20), 1, TEN);

        assertThrows(
                InsufficientBalanceException.class,
                () -> selectProduct.handle(command(
                        machineId, sessionId, SLOT_CODE, selectionKey)));

        assertEquals(0, count("purchase", "machine_id", machineId.value()));
        assertEquals(
                0,
                count("processed_command", "idempotency_key", selectionKey.value()));
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "select version from vending_machine where machine_id = ?",
                        Long.class,
                        machineId.value()));
        var afterFailure = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(1, afterFailure.stockOf(SLOT_CODE));
        assertEquals(Money.of(10, UNIT), afterFailure.snapshot()
                .currentSession()
                .orElseThrow()
                .escrow()
                .total());

        insertMoney.handle(new InsertMoneyCommand(
                machineId,
                sessionId,
                "SIM-VALID-10",
                new IdempotencyKey("KEY-PURCHASE-TOP-UP")));
        var retry = selectProduct.handle(command(
                machineId, sessionId, SLOT_CODE, selectionKey));

        assertEquals(Money.of(20, UNIT), retry.purchase().insertedAmount());
        assertEquals(1, count("purchase", "machine_id", machineId.value()));
        assertEquals(
                1,
                count("processed_command", "idempotency_key", selectionKey.value()));
    }

    @Test
    void eventHandlerFailureRollsBackPurchaseStateAndCommandClaim() {
        var machineId = new MachineId(FailingPurchaseListener.FAILING_MACHINE_ID);
        var sessionId = new SessionId("SES-PURCHASE-HANDLER-FAILURE");
        var selectionKey = new IdempotencyKey("KEY-PURCHASE-HANDLER-FAILURE");
        persistMachine(machineId, sessionId, productPricedAt(35), 1, FIFTY);

        assertThrows(
                IllegalStateException.class,
                () -> selectProduct.handle(command(
                        machineId, sessionId, SLOT_CODE, selectionKey)));

        assertEquals(0, count("purchase", "machine_id", machineId.value()));
        assertEquals(
                0,
                count("processed_command", "idempotency_key", selectionKey.value()));
        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(1, restored.stockOf(SLOT_CODE));
        assertEquals(sessionId, restored.activeSessionId().orElseThrow());
        assertEquals(CashComposition.of(Map.of(TEN, 1, FIVE, 1)), restored.availableCash());
        assertEquals(Money.of(50, UNIT), restored.snapshot()
                .currentSession()
                .orElseThrow()
                .escrow()
                .total());
    }

    @Test
    void concurrentSameKeyCommandsCommitOnePurchaseAndReturnOneResult() throws Exception {
        var machineId = new MachineId("VM-PURCHASE-CONCURRENT");
        var sessionId = new SessionId("SES-PURCHASE-CONCURRENT");
        var key = new IdempotencyKey("KEY-PURCHASE-CONCURRENT");
        persistMachine(machineId, sessionId, productPricedAt(35), 2, FIFTY);
        var bothCommandsReady = new CyclicBarrier(2);

        SelectProductResult first;
        SelectProductResult second;
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstCall = workers.submit(() -> {
                await(bothCommandsReady);
                return selectProduct.handle(command(machineId, sessionId, SLOT_CODE, key));
            });
            var secondCall = workers.submit(() -> {
                await(bothCommandsReady);
                return selectProduct.handle(command(machineId, sessionId, SLOT_CODE, key));
            });
            first = firstCall.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second = secondCall.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertEquals(first, second);
        assertEquals(1, count("purchase", "machine_id", machineId.value()));
        assertEquals(1, count("processed_command", "machine_id", machineId.value()));
        var restored = transactions.execute(
                status -> machineRepository.findById(machineId).orElseThrow());
        assertEquals(1, restored.stockOf(SLOT_CODE));
    }

    private void persistMachine(
            MachineId machineId,
            SessionId sessionId,
            ProductSnapshot product,
            int stock,
            Denomination inserted) {
        var machine = new VendingMachine(
                machineId,
                CashComposition.of(Map.of(TEN, 1, FIVE, 1)),
                List.of(new Slot(SLOT_CODE, product, stock)));
        machine.startSession(sessionId, STARTED_AT);
        machine.acceptMoney(sessionId, inserted, STARTED_AT.plusSeconds(10));
        machine.releaseEvents();
        transactions.executeWithoutResult(status -> machineRepository.save(machine));
    }

    private int count(String table, String idColumn, String id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + idColumn + " = ?",
                Integer.class,
                id);
    }

    private static SelectProductCommand command(
            MachineId machineId,
            SessionId sessionId,
            SlotCode slotCode,
            IdempotencyKey key) {
        return new SelectProductCommand(
                machineId,
                sessionId,
                slotCode,
                key);
    }

    private static ProductSnapshot productPricedAt(long amount) {
        return new ProductSnapshot(PRODUCT_ID, "Cola", Money.of(amount, UNIT));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not synchronize purchase commands", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingPurchaseListenerConfiguration {

        @Bean
        FailingPurchaseListener failingPurchaseListener() {
            return new FailingPurchaseListener();
        }
    }

    static final class FailingPurchaseListener {

        private static final String FAILING_MACHINE_ID = "VM-PURCHASE-HANDLER-FAILURE";

        @EventListener
        void failForConfiguredMachine(PurchaseCompleted event) {
            if (event.purchase().machineId().value().equals(FAILING_MACHINE_ID)) {
                throw new IllegalStateException("Simulated purchase event handler failure");
            }
        }
    }
}
