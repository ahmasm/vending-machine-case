package io.github.ahmasm.vending.machine.web;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.command.MachineNotFoundException;
import io.github.ahmasm.vending.machine.application.command.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.command.RefundResult;
import io.github.ahmasm.vending.machine.application.command.SelectProductResult;
import io.github.ahmasm.vending.machine.application.command.StartSessionResult;
import io.github.ahmasm.vending.machine.application.money.CurrencyAcceptanceAlreadyConsumedException;
import io.github.ahmasm.vending.machine.application.money.CurrencyRejectedException;
import io.github.ahmasm.vending.machine.application.money.CurrencyValidationUnavailableException;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyService;
import io.github.ahmasm.vending.machine.application.purchase.SelectProductCommand;
import io.github.ahmasm.vending.machine.application.refund.RefundCommand;
import io.github.ahmasm.vending.machine.application.session.StartSessionCommand;
import io.github.ahmasm.vending.machine.application.money.CurrencyRejectionReason;
import io.github.ahmasm.vending.machine.application.purchase.SelectProductService;
import io.github.ahmasm.vending.machine.application.refund.RefundService;
import io.github.ahmasm.vending.machine.application.session.StartSessionService;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionAlreadyExistsException;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.ChangeUnavailableException;
import io.github.ahmasm.vending.machine.domain.machine.InsufficientBalanceException;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductOutOfStockException;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.SlotNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(VendingCommandController.class)
@ContextConfiguration(classes = {
    VendingCommandController.class,
    ApiExceptionHandler.class,
    CorrelationIdFilter.class
})
class VendingCommandControllerTest {

    private static final String CORRELATION_ID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final MachineId MACHINE_ID = new MachineId("VM-001");
    private static final String CLIENT_CORRELATION_ID =
            "00000000-0000-4000-8000-000000000000";
    private static final UUID SESSION_UUID =
            UUID.fromString("6f9619ff-8b86-d011-b42d-00c04fc964ff");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private StartSessionService startSessionUseCase;

    @MockitoBean
    private InsertMoneyService insertMoneyUseCase;

    @MockitoBean
    private SelectProductService selectProductUseCase;

    @MockitoBean
    private RefundService refundUseCase;

    @Autowired
    VendingCommandControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void startSessionMapsHeaderAndPathAndReturnsCreatedResource() throws Exception {
        var startedAt = Instant.parse("2026-08-23T10:00:00Z");
        when(startSessionUseCase.handle(any()))
                .thenReturn(new StartSessionResult(new SessionId(SESSION_UUID.toString()), startedAt));

        var result = mockMvc.perform(post("/api/v1/machines/{machineId}/sessions", MACHINE_ID.value())
                        .header("Idempotency-Key", "KEY-START")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, CLIENT_CORRELATION_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        CorrelationIdFilter.CORRELATION_ID_HEADER,
                        matchesPattern(CORRELATION_ID_PATTERN)))
                .andExpect(header().string(
                        "Location",
                        "/api/v1/machines/VM-001/sessions/" + SESSION_UUID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionId").value(SESSION_UUID.toString()))
                .andExpect(jsonPath("$.startedAt").value(startedAt.toString()))
                .andReturn();

        var commandCaptor = ArgumentCaptor.forClass(StartSessionCommand.class);
        verify(startSessionUseCase).handle(commandCaptor.capture());
        var command = commandCaptor.getValue();
        assertEquals(MACHINE_ID, command.machineId());
        assertEquals("KEY-START", command.idempotencyKey().value());
        assertNotEquals(
                CLIENT_CORRELATION_ID,
                result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void insertMoneyMapsValidatorReferenceAndReturnsCurrentBalance() throws Exception {
        when(insertMoneyUseCase.handle(any()))
                .thenReturn(new InsertMoneyResult(Money.of(10, UNIT)));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/money",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-MONEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validatorReference": "SIM-VALID-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.balance.amount").value(10))
                .andExpect(jsonPath("$.balance.currency").value("UNIT"));

        var commandCaptor = ArgumentCaptor.forClass(InsertMoneyCommand.class);
        verify(insertMoneyUseCase).handle(commandCaptor.capture());
        var command = commandCaptor.getValue();
        assertEquals(MACHINE_ID, command.machineId());
        assertEquals(new SessionId(SESSION_UUID.toString()), command.sessionId());
        assertEquals("SIM-VALID-10", command.validatorReference());
        assertEquals("KEY-MONEY", command.idempotencyKey().value());
    }

    @Test
    void selectProductMapsTransportValuesAndReturnsCompletedPurchase() throws Exception {
        var completedAt = Instant.parse("2026-08-23T10:05:00Z");
        when(selectProductUseCase.handle(any()))
                .thenReturn(new SelectProductResult(new Purchase(
                        new TransactionId("TRX-001"),
                        MACHINE_ID,
                        new SessionId(SESSION_UUID.toString()),
                        new SlotCode("A2"),
                        new ProductSnapshot(
                                new ProductId("COKE"), "Coke", Money.of(35, UNIT)),
                        Money.of(50, UNIT),
                        CashComposition.of(Map.of(TEN, 1, FIVE, 1)),
                        completedAt)));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/selection",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-SELECTION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotCode": "A2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").value("TRX-001"))
                .andExpect(jsonPath("$.machineId").value("VM-001"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_UUID.toString()))
                .andExpect(jsonPath("$.slotCode").value("A2"))
                .andExpect(jsonPath("$.product.id").value("COKE"))
                .andExpect(jsonPath("$.product.name").value("Coke"))
                .andExpect(jsonPath("$.price.amount").value(35))
                .andExpect(jsonPath("$.price.currency").value("UNIT"))
                .andExpect(jsonPath("$.insertedAmount.amount").value(50))
                .andExpect(jsonPath("$.insertedAmount.currency").value("UNIT"))
                .andExpect(jsonPath("$.change.total.amount").value(15))
                .andExpect(jsonPath("$.change.total.currency").value("UNIT"))
                .andExpect(jsonPath("$['change']['composition']['10']").value(1))
                .andExpect(jsonPath("$['change']['composition']['5']").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        var commandCaptor = ArgumentCaptor.forClass(SelectProductCommand.class);
        verify(selectProductUseCase).handle(commandCaptor.capture());
        var command = commandCaptor.getValue();
        assertEquals(MACHINE_ID, command.machineId());
        assertEquals(new SessionId(SESSION_UUID.toString()), command.sessionId());
        assertEquals(new SlotCode("A2"), command.slotCode());
        assertEquals("KEY-SELECTION", command.idempotencyKey().value());
    }

    @Test
    void refundMapsTransportValuesAndReturnsExactEscrowComposition() throws Exception {
        when(refundUseCase.handle(any()))
                .thenReturn(new RefundResult(CashComposition.of(Map.of(TEN, 1, FIVE, 1))));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/refund",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-REFUND"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.machineId").value("VM-001"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_UUID.toString()))
                .andExpect(jsonPath("$.returnedCash.total.amount").value(15))
                .andExpect(jsonPath("$.returnedCash.total.currency").value("UNIT"))
                .andExpect(jsonPath("$['returnedCash']['composition']['10']").value(1))
                .andExpect(jsonPath("$['returnedCash']['composition']['5']").value(1))
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        var commandCaptor = ArgumentCaptor.forClass(RefundCommand.class);
        verify(refundUseCase).handle(commandCaptor.capture());
        var command = commandCaptor.getValue();
        assertEquals(MACHINE_ID, command.machineId());
        assertEquals(new SessionId(SESSION_UUID.toString()), command.sessionId());
        assertEquals("KEY-REFUND", command.idempotencyKey().value());
    }

    @Test
    void refundWithoutIdempotencyKeyReturnsBadRequestWithoutCallingApplication()
            throws Exception {
        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/refund",
                                MACHINE_ID.value(),
                                SESSION_UUID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(refundUseCase, never()).handle(any());
    }

    @Test
    void refundWithoutRequestedActiveSessionReturnsConflictProblem() throws Exception {
        var sessionId = new SessionId(SESSION_UUID.toString());
        when(refundUseCase.handle(any()))
                .thenThrow(new ActiveSessionNotFoundException(MACHINE_ID, sessionId));

        var result = mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/refund",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-REFUND-CONFLICT"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-001/sessions/"
                                + SESSION_UUID
                                + "/refund"))
                .andReturn();

        assertProblemCorrelationMatchesHeader(result);
    }

    @Test
    void selectionWithoutIdempotencyKeyReturnsBadRequestWithoutCallingApplication()
            throws Exception {
        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/selection",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotCode": "A2"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(selectProductUseCase, never()).handle(any());
    }

    @Test
    void blankSelectionSlotReturnsBadRequestWithoutCallingApplication() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/selection",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-SELECTION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotCode": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(selectProductUseCase, never()).handle(any());
    }

    @ParameterizedTest
    @MethodSource("selectionFailures")
    void selectionFailureUsesStableProblemContract(
            RuntimeException failure,
            int expectedStatus,
            String expectedCode,
            String safeProperty,
            Object safeValue)
            throws Exception {
        when(selectProductUseCase.handle(any())).thenThrow(failure);

        var response = mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/selection",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-SELECTION-FAILURE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotCode": "A2"}
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$." + safeProperty).value(safeValue))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-001/sessions/"
                                + SESSION_UUID
                                + "/selection"))
                .andReturn();

        if (failure instanceof InsufficientBalanceException) {
            assertEquals(
                    35,
                    objectMapper
                            .readTree(response.getResponse().getContentAsString())
                            .path("productPrice")
                            .asLong());
        }
        assertProblemCorrelationMatchesHeader(response);
    }

    @Test
    void missingIdempotencyKeyReturnsCorrelatedProblemDetail() throws Exception {
        var result = mockMvc.perform(post("/api/v1/machines/{machineId}/sessions", MACHINE_ID.value()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:vending-machine:problem:invalid-request"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-001/sessions"))
                .andReturn();

        assertProblemCorrelationMatchesHeader(result);
        verify(startSessionUseCase, never()).handle(any());
    }

    @ParameterizedTest
    @MethodSource("invalidMoneyBodies")
    void invalidMoneyBodyReturnsBadRequestWithoutCallingApplication(String body) throws Exception {
        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/money",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-INVALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(insertMoneyUseCase, never()).handle(any());
    }

    @ParameterizedTest
    @MethodSource("startSessionFailures")
    void startSessionFailureUsesStableProblemContract(
            RuntimeException failure, int expectedStatus, String expectedCode) throws Exception {
        when(startSessionUseCase.handle(any())).thenThrow(failure);

        var result = mockMvc.perform(post("/api/v1/machines/{machineId}/sessions", MACHINE_ID.value())
                        .header("Idempotency-Key", "KEY-FAILURE"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn();

        assertProblemCorrelationMatchesHeader(result);
    }

    @Test
    void currencyValidatorOutageReturnsServiceUnavailableProblem() throws Exception {
        when(insertMoneyUseCase.handle(any()))
                .thenThrow(new CurrencyValidationUnavailableException(MACHINE_ID));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/money",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-UNAVAILABLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validatorReference": "SIM-OFFLINE"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CURRENCY_VALIDATION_UNAVAILABLE"));
    }

    @Test
    void rejectedCurrencyReturnsConflictProblemWithReason() throws Exception {
        when(insertMoneyUseCase.handle(any()))
                .thenThrow(new CurrencyRejectedException(
                        MACHINE_ID, CurrencyRejectionReason.COUNTERFEIT));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/money",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-COUNTERFEIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validatorReference": "SIM-COUNTERFEIT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CURRENCY_REJECTED"))
                .andExpect(jsonPath("$.reason").value("COUNTERFEIT"));
    }

    @Test
    void consumedCurrencyAcceptanceReturnsConflictProblem() throws Exception {
        when(insertMoneyUseCase.handle(any()))
                .thenThrow(new CurrencyAcceptanceAlreadyConsumedException(MACHINE_ID));

        mockMvc.perform(post(
                                "/api/v1/machines/{machineId}/sessions/{sessionId}/money",
                                MACHINE_ID.value(),
                                SESSION_UUID)
                        .header("Idempotency-Key", "KEY-CURRENCY-REPLAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validatorReference": "SIM-VALID-10-DEMO-001"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code")
                        .value("CURRENCY_ACCEPTANCE_ALREADY_CONSUMED"));
    }

    @Test
    void unexpectedFailureReturnsSanitizedInternalError() throws Exception {
        when(startSessionUseCase.handle(any()))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        var result = mockMvc.perform(post("/api/v1/machines/{machineId}/sessions", MACHINE_ID.value())
                        .header("Idempotency-Key", "KEY-INTERNAL"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andReturn();

        var response = result.getResponse().getContentAsString();
        assertFalse(response.contains("sensitive internal detail"));
        assertProblemCorrelationMatchesHeader(result);
    }

    @Test
    void unsupportedHttpMethodPreservesMethodNotAllowedStatus() throws Exception {
        var result = mockMvc.perform(put("/api/v1/machines/{machineId}/sessions", MACHINE_ID.value()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andReturn();

        assertProblemCorrelationMatchesHeader(result);
    }

    private void assertProblemCorrelationMatchesHeader(MvcResult result) throws Exception {
        var responseCorrelationId =
                result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        UUID.fromString(responseCorrelationId);
        var problem = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(responseCorrelationId, problem.path("correlationId").asText());
    }

    private static Stream<String> invalidMoneyBodies() {
        return Stream.of(
                "{}",
                "{\"validatorReference\": \"\"}",
                "{\"validatorReference\": \"   \"}");
    }

    private static Stream<Arguments> startSessionFailures() {
        var activeSessionId = new SessionId(SESSION_UUID.toString());
        return Stream.of(
                Arguments.of(
                        new MachineNotFoundException(MACHINE_ID),
                        404,
                        "MACHINE_NOT_FOUND"),
                Arguments.of(
                        new ActiveSessionAlreadyExistsException(MACHINE_ID, activeSessionId),
                        409,
                        "ACTIVE_SESSION_ALREADY_EXISTS"),
                Arguments.of(
                        new IdempotencyKeyReusedException(MACHINE_ID),
                        409,
                        "IDEMPOTENCY_KEY_REUSED"));
    }

    private static Stream<Arguments> selectionFailures() {
        var slotCode = new SlotCode("A2");
        return Stream.of(
                Arguments.of(
                        new InsufficientBalanceException(
                                Money.of(20, UNIT), Money.of(35, UNIT)),
                        409,
                        "INSUFFICIENT_BALANCE",
                        "balance",
                        20),
                Arguments.of(
                        new ProductOutOfStockException(slotCode),
                        409,
                        "PRODUCT_OUT_OF_STOCK",
                        "slotCode",
                        "A2"),
                Arguments.of(
                        new ChangeUnavailableException(Money.of(15, UNIT)),
                        409,
                        "CHANGE_UNAVAILABLE",
                        "changeDue",
                        15),
                Arguments.of(
                        new SlotNotFoundException(slotCode),
                        404,
                        "SLOT_NOT_FOUND",
                        "slotCode",
                        "A2"));
    }
}
