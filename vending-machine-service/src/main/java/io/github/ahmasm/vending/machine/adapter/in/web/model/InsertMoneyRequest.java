package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "InsertMoneyRequest")
public record InsertMoneyRequest(
        @NotNull @Positive @Schema(example = "10") Long denomination) {}
