package io.github.ahmasm.vending.machine.adapter.in.web;

import io.github.ahmasm.vending.machine.adapter.in.web.model.ApiProblem;
import io.github.ahmasm.vending.machine.adapter.in.web.model.CurrencyCode;
import io.github.ahmasm.vending.machine.adapter.in.web.model.ListProductsResponse;
import io.github.ahmasm.vending.machine.adapter.in.web.model.ListProductsResponse.ProductAvailabilityResponse;
import io.github.ahmasm.vending.machine.adapter.in.web.model.MoneyResponse;
import io.github.ahmasm.vending.machine.adapter.in.web.model.ProductResponse;
import io.github.ahmasm.vending.machine.adapter.in.web.model.SelectProductResponse;
import io.github.ahmasm.vending.machine.adapter.in.web.model.SessionResponse;
import io.github.ahmasm.vending.machine.application.product.ListProductsHandler;
import io.github.ahmasm.vending.machine.application.purchase.GetPurchaseHandler;
import io.github.ahmasm.vending.machine.application.session.GetSessionHandler;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/machines/{machineId}")
@Validated
@Tag(name = "Vending queries", description = "Customer-facing vending machine queries")
public class VendingQueryController {

    private final ListProductsHandler listProductsHandler;
    private final GetSessionHandler getSessionHandler;
    private final GetPurchaseHandler getPurchaseHandler;

    public VendingQueryController(
            ListProductsHandler listProductsHandler,
            GetSessionHandler getSessionHandler,
            GetPurchaseHandler getPurchaseHandler) {
        this.listProductsHandler =
                Objects.requireNonNull(listProductsHandler, "listProductsHandler must not be null");
        this.getSessionHandler =
                Objects.requireNonNull(getSessionHandler, "getSessionHandler must not be null");
        this.getPurchaseHandler =
                Objects.requireNonNull(getPurchaseHandler, "getPurchaseHandler must not be null");
    }

    @GetMapping(path = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List current product availability")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Product availability listed",
                content = @Content(schema = @Schema(implementation = ListProductsResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid machine identifier",
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
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<ListProductsResponse> listProducts(
            @PathVariable @NotBlank @Size(max = 64) String machineId) {
        var result = listProductsHandler.handle(new MachineId(machineId));
        var products = result.products().stream()
                .map(product -> new ProductAvailabilityResponse(
                        product.slotCode().value(),
                        new ProductResponse(
                                product.product().id().value(),
                                product.product().name()),
                        new MoneyResponse(
                                product.product().price().amount(),
                                CurrencyCode.valueOf(
                                        product.product().price().currency().name())),
                        product.availability().name()))
                .toList();
        return ResponseEntity.ok(new ListProductsResponse(result.machineId().value(), products));
    }

    @GetMapping(path = "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get current or terminal session state")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Session state returned",
                content = @Content(schema = @Schema(implementation = SessionResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid machine or session identifier",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Session not found for machine",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @PathVariable UUID sessionId) {
        var domainMachineId = new MachineId(machineId);
        var state = getSessionHandler.handle(
                domainMachineId, new SessionId(sessionId.toString()));
        return ResponseEntity.ok(new SessionResponse(
                domainMachineId.value(),
                state.id().value(),
                state.status().name(),
                new MoneyResponse(
                        state.escrow().total().amount(), CurrencyCode.UNIT),
                state.startedAt(),
                state.lastActivityAt()));
    }

    @GetMapping(path = "/purchases/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an authoritative completed purchase")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Purchase returned",
                content = @Content(schema = @Schema(implementation = SelectProductResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid machine or transaction identifier",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Purchase not found for machine",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected internal error",
                content = @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<SelectProductResponse> getPurchase(
            @PathVariable @NotBlank @Size(max = 64) String machineId,
            @PathVariable @NotBlank @Size(max = 128) String transactionId) {
        var purchase = getPurchaseHandler.handle(
                new MachineId(machineId), new TransactionId(transactionId));
        return ResponseEntity.ok(SelectProductResponse.from(purchase));
    }
}
