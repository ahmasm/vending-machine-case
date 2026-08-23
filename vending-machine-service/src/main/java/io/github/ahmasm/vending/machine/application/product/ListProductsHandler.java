package io.github.ahmasm.vending.machine.application.product;

import static io.github.ahmasm.vending.machine.application.product.ListProductsHandler.Availability.AVAILABLE;
import static io.github.ahmasm.vending.machine.application.product.ListProductsHandler.Availability.OUT_OF_STOCK;

import io.github.ahmasm.vending.machine.application.command.MachineNotFoundException;
import io.github.ahmasm.vending.machine.application.port.out.ProductCatalogReader;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class ListProductsHandler {

    private final ProductCatalogReader productCatalogReader;

    public ListProductsHandler(ProductCatalogReader productCatalogReader) {
        this.productCatalogReader = Objects.requireNonNull(
                productCatalogReader, "productCatalogReader must not be null");
    }

    public Result handle(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        var slots = productCatalogReader
                .findSlots(machineId)
                .orElseThrow(() -> new MachineNotFoundException(machineId));
        var products = slots.stream()
                .map(slot -> new ListedProduct(
                        slot.code(),
                        slot.product(),
                        slot.quantity() > 0 ? AVAILABLE : OUT_OF_STOCK))
                .toList();
        return new Result(machineId, products);
    }

    public enum Availability {
        AVAILABLE,
        OUT_OF_STOCK
    }

    public record ListedProduct(
            SlotCode slotCode, ProductSnapshot product, Availability availability) {

        public ListedProduct {
            Objects.requireNonNull(slotCode, "slotCode must not be null");
            Objects.requireNonNull(product, "product must not be null");
            Objects.requireNonNull(availability, "availability must not be null");
        }
    }

    public record Result(MachineId machineId, List<ListedProduct> products) {

        public Result {
            Objects.requireNonNull(machineId, "machineId must not be null");
            products = List.copyOf(Objects.requireNonNull(products, "products must not be null"));
        }
    }
}
