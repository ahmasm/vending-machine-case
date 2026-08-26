package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "InsertMoneyRequest")
public record InsertMoneyRequest(
        @NotBlank
                @Size(max = 128)
                @Schema(
                        description = "Reference produced by the trusted currency validator",
                        example = "SIM-VALID-10-DEMO-001")
                String validatorReference) {}
