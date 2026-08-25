package io.github.ahmasm.vending.machine.application.event;

import io.github.ahmasm.vending.machine.application.purchase.PurchaseStore;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseCompleted;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public final class PurchaseCompletedEventHandler {

    private final PurchaseStore purchaseStore;

    public PurchaseCompletedEventHandler(PurchaseStore purchaseStore) {
        this.purchaseStore = Objects.requireNonNull(purchaseStore, "purchaseStore must not be null");
    }

    @EventListener
    public void handle(PurchaseCompleted purchaseCompleted) {
        Objects.requireNonNull(purchaseCompleted, "purchaseCompleted must not be null");
        purchaseStore.save(purchaseCompleted.purchase());
    }
}
