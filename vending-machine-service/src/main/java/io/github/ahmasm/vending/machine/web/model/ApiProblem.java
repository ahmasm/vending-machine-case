package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;

@Schema(name = "ApiProblem", description = "RFC 9457 problem detail with stable case fields")
public record ApiProblem(
        @Schema(example = "urn:vending-machine:problem:idempotency-key-reused") URI type,
        @Schema(example = "Idempotency key reused") String title,
        @Schema(example = "409") int status,
        @Schema(example = "IDEMPOTENCY_KEY_REUSED") String code,
        @Schema(example = "The idempotency key was already used for another command") String detail,
        @Schema(example = "/api/v1/machines/VM-001/sessions") URI instance,
        @Schema(example = "f47ac10b-58cc-4372-a567-0e02b2c3d479") String correlationId) {}
