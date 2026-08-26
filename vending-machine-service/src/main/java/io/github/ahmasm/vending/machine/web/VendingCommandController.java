package io.github.ahmasm.vending.machine.web;

import io.github.ahmasm.vending.machine.web.model.ApiProblem;
import io.github.ahmasm.vending.machine.web.model.InsertMoneyRequest;
import io.github.ahmasm.vending.machine.web.model.InsertMoneyResponse;
import io.github.ahmasm.vending.machine.web.model.MoneyResponse;
import io.github.ahmasm.vending.machine.web.model.RefundResponse;
import io.github.ahmasm.vending.machine.web.model.ReturnedCashResponse;
import io.github.ahmasm.vending.machine.web.model.SelectProductRequest;
import io.github.ahmasm.vending.machine.web.model.SelectProductResponse;
import io.github.ahmasm.vending.machine.web.model.StartSessionResponse;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.command.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.command.RefundResult;
import io.github.ahmasm.vending.machine.application.command.SelectProductResult;
import io.github.ahmasm.vending.machine.application.command.StartSessionResult;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.money.InsertMoneyService;
import io.github.ahmasm.vending.machine.application.purchase.SelectProductCommand;
import io.github.ahmasm.vending.machine.application.refund.RefundCommand;
import io.github.ahmasm.vending.machine.application.session.StartSessionCommand;
import io.github.ahmasm.vending.machine.application.purchase.SelectProductService;
import io.github.ahmasm.vending.machine.application.refund.RefundService;
import io.github.ahmasm.vending.machine.application.session.StartSessionService;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Validated
@RequestMapping("/api/v1/machines/{machineId}")
@Tag(name = "Vending commands", description = "Customer-facing vending machine commands")
public class VendingCommandController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final StartSessionService startSessionService;
    private final InsertMoneyService insertMoneyService;
    private final SelectProductService selectProductService;
    private final RefundService refundService;

    public VendingCommandController(
            StartSessionService startSessionService,
            InsertMoneyService insertMoneyService,
            SelectProductService selectProductService,
            RefundService refundService) {
        this.startSessionService =
                Objects.requireNonNull(startSessionService, "startSessionService must not be null");
        this.insertMoneyService =
                Objects.requireNonNull(insertMoneyService, "insertMoneyService must not be null");
        this.selectProductService = Objects.requireNonNull(
                selectProductService, "selectProductService must not be null");
        this.refundService = Objects.requireNonNull(refundService, "refundService must not be null");
    }

    @PostMapping(path = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start a purchase session")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Session started",
                content = @Content(schema = @Schema(implementation = StartSessionResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Machine not found",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Active session, idempotency, or concurrency conflict",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<StartSessionResponse> startSession(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @Parameter(description = "Machine-scoped retry key", required = true)
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @NotBlank
                    @Size(max = 128)
                    String idempotencyKey) {
        var result = startSessionService.handle(new StartSessionCommand(
                new MachineId(machineId),
                new IdempotencyKey(idempotencyKey)));
        return ResponseEntity.created(sessionLocation(machineId, result))
                .body(new StartSessionResponse(
                        result.sessionId().value(), result.startedAt()));
    }

    @PostMapping(
            path = "/sessions/{sessionId}/money",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Insert currency validated by the trusted device boundary")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Money accepted",
                content = @Content(schema = @Schema(implementation = InsertMoneyResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Machine not found",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Session, validation, idempotency, or concurrency conflict",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Currency validator unavailable",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<InsertMoneyResponse> insertMoney(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @PathVariable UUID sessionId,
            @Parameter(description = "Machine-scoped retry key", required = true)
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @NotBlank
                    @Size(max = 128)
                    String idempotencyKey,
            @Valid @RequestBody InsertMoneyRequest body) {
        var result = insertMoneyService.handle(new InsertMoneyCommand(
                new MachineId(machineId),
                new SessionId(sessionId.toString()),
                body.validatorReference(),
                new IdempotencyKey(idempotencyKey)));
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping(
            path = "/sessions/{sessionId}/selection",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Select a product and complete the purchase")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Purchase completed",
                content = @Content(schema = @Schema(implementation = SelectProductResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Machine or slot not found",
                content = @Content(schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Session, balance, stock, change, idempotency, or concurrency conflict",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class),
                        examples = @ExampleObject(
                                name = "Insufficient balance",
                                value = """
                                        {
                                          "type": "urn:vending-machine:problem:insufficient-balance",
                                          "title": "Insufficient balance",
                                          "status": 409,
                                          "code": "INSUFFICIENT_BALANCE",
                                          "detail": "Current balance is not sufficient for the selected product",
                                          "instance": "/api/v1/machines/VM-001/sessions/550e8400-e29b-41d4-a716-446655440000/selection",
                                          "correlationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                                          "balance": 20,
                                          "productPrice": 35
                                        }
                                        """))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SelectProductResponse> selectProduct(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @PathVariable UUID sessionId,
            @Parameter(description = "Machine-scoped retry key", required = true)
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @NotBlank
                    @Size(max = 128)
                    String idempotencyKey,
            @Valid @RequestBody SelectProductRequest body) {
        var result = selectProductService.handle(new SelectProductCommand(
                new MachineId(machineId),
                new SessionId(sessionId.toString()),
                new SlotCode(body.slotCode()),
                new IdempotencyKey(idempotencyKey)));
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping(
            path = "/sessions/{sessionId}/refund",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Refund the exact escrow of an active session")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Escrow refunded",
                content = @Content(schema = @Schema(implementation = RefundResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Machine not found",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Session, idempotency, or concurrency conflict",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class),
                        examples = @ExampleObject(
                                name = "Active session not found",
                                value = """
                                        {
                                          "type": "urn:vending-machine:problem:active-session-not-found",
                                          "title": "Active session not found",
                                          "status": 409,
                                          "code": "ACTIVE_SESSION_NOT_FOUND",
                                          "detail": "The requested active session does not exist",
                                          "instance": "/api/v1/machines/VM-001/sessions/550e8400-e29b-41d4-a716-446655440000/refund",
                                          "correlationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
                                        }
                                        """))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<RefundResponse> refund(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @PathVariable UUID sessionId,
            @Parameter(description = "Machine-scoped retry key", required = true)
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @NotBlank
                    @Size(max = 128)
                    String idempotencyKey) {
        var command = new RefundCommand(
                new MachineId(machineId),
                new SessionId(sessionId.toString()),
                new IdempotencyKey(idempotencyKey));
        var result = refundService.handle(command);
        return ResponseEntity.ok(toResponse(command, result));
    }

    private static URI sessionLocation(String machineId, StartSessionResult result) {
        return UriComponentsBuilder.fromPath(
                        "/api/v1/machines/{machineId}/sessions/{sessionId}")
                .buildAndExpand(machineId, result.sessionId().value())
                .encode()
                .toUri();
    }

    private static InsertMoneyResponse toResponse(InsertMoneyResult result) {
        return new InsertMoneyResponse(MoneyResponse.from(result.balance()));
    }

    private static SelectProductResponse toResponse(SelectProductResult result) {
        return SelectProductResponse.from(result.purchase());
    }

    private static RefundResponse toResponse(RefundCommand command, RefundResult result) {
        return new RefundResponse(
                command.machineId().value(),
                command.sessionId().value(),
                new ReturnedCashResponse(
                        MoneyResponse.from(result.returnedCash().total()),
                        compositionOf(result.returnedCash())),
                "REFUNDED");
    }

    private static Map<String, Integer> compositionOf(CashComposition composition) {
        var quantities = new LinkedHashMap<String, Integer>();
        for (var denomination : Denomination.values()) {
            var quantity = composition.quantityOf(denomination);
            if (quantity > 0) {
                quantities.put(Long.toString(denomination.value().amount()), quantity);
            }
        }
        return Map.copyOf(quantities);
    }

}
