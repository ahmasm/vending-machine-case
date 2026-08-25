package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(name = "RefundResponse")
public record RefundResponse(
        @Schema(example = "VM-001") String machineId,
        @Schema(example = "6f9619ff-8b86-d011-b42d-00c04fc964ff") String sessionId,
        ReturnedCashResponse returnedCash,
        @Schema(example = "REFUNDED") String status) {

    public RefundResponse {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(returnedCash, "returnedCash must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
