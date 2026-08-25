package io.github.ahmasm.vending.machine.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(name = "Product")
public record ProductResponse(
        @Schema(example = "COKE") String id,
        @Schema(example = "Coke") String name) {

    public ProductResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
