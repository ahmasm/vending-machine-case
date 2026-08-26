package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;

@Schema(name = "Change")
public record ChangeResponse(
        MoneyResponse total,
        Map<String, Integer> composition) {

    public ChangeResponse {
        Objects.requireNonNull(total, "total must not be null");
        composition = Map.copyOf(
                Objects.requireNonNull(composition, "composition must not be null"));
    }
}
