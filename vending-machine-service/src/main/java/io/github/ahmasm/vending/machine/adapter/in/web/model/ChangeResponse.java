package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;

@Schema(name = "Change")
public record ChangeResponse(
        @Schema(example = "15") long amount,
        Map<String, Integer> composition) {

    public ChangeResponse {
        composition = Map.copyOf(
                Objects.requireNonNull(composition, "composition must not be null"));
    }
}
