package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Money")
public record MoneyResponse(
        @Schema(example = "10") long amount,
        @Schema(example = "UNIT") CurrencyCode currency) {}
