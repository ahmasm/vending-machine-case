package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(name = "ListProductsResponse")
public record ListProductsResponse(
        @Schema(example = "VM-001") String machineId,
        List<ProductAvailabilityResponse> products) {

    public ListProductsResponse {
        Objects.requireNonNull(machineId, "machineId must not be null");
        products = List.copyOf(Objects.requireNonNull(products, "products must not be null"));
    }

    @Schema(name = "ProductAvailability")
    public record ProductAvailabilityResponse(
            @Schema(example = "A1") String slotCode,
            ProductResponse product,
            MoneyResponse price,
            @Schema(example = "AVAILABLE") String availability) {

        public ProductAvailabilityResponse {
            Objects.requireNonNull(slotCode, "slotCode must not be null");
            Objects.requireNonNull(product, "product must not be null");
            Objects.requireNonNull(price, "price must not be null");
            Objects.requireNonNull(availability, "availability must not be null");
        }
    }
}
