package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SelectProductRequest")
public record SelectProductRequest(
        @Schema(example = "A2") @NotBlank @Size(max = 64) String slotCode) {}
