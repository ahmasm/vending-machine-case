package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Schema(name = "SelectProductResponse")
public record SelectProductResponse(
        @Schema(example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8") String transactionId,
        @Schema(example = "VM-001") String machineId,
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000") String sessionId,
        @Schema(example = "A2") String slotCode,
        ProductResponse product,
        @Schema(example = "35") long price,
        @Schema(example = "50") long insertedAmount,
        ChangeResponse change,
        @Schema(example = "COMPLETED") String status) {

    public SelectProductResponse {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(slotCode, "slotCode must not be null");
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(change, "change must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static SelectProductResponse from(Purchase purchase) {
        Objects.requireNonNull(purchase, "purchase must not be null");
        var composition = new LinkedHashMap<String, Integer>();
        for (var denomination : Denomination.values()) {
            var quantity = purchase.change().quantityOf(denomination);
            if (quantity > 0) {
                composition.put(Long.toString(denomination.value().amount()), quantity);
            }
        }
        return new SelectProductResponse(
                purchase.transactionId().value(),
                purchase.machineId().value(),
                purchase.sessionId().value(),
                purchase.slotCode().value(),
                new ProductResponse(
                        purchase.product().id().value(), purchase.product().name()),
                purchase.product().price().amount(),
                purchase.insertedAmount().amount(),
                new ChangeResponse(purchase.change().total().amount(), Map.copyOf(composition)),
                "COMPLETED");
    }
}
