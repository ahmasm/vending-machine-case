package io.github.ahmasm.vending.machine.adapter.out.persistence.machine;

import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.SlotState;
import io.github.ahmasm.vending.machine.domain.money.Currency;
import io.github.ahmasm.vending.machine.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "machine_slot")
public class MachineSlotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private VendingMachineJpaEntity machine;

    @Column(name = "slot_code", nullable = false, columnDefinition = "text")
    private String slotCode;

    @Column(name = "product_id", nullable = false, columnDefinition = "text")
    private String productId;

    @Column(name = "product_name", nullable = false, columnDefinition = "text")
    private String productName;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_currency", nullable = false, length = 16)
    private Currency priceCurrency;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected MachineSlotJpaEntity() {}

    MachineSlotJpaEntity(VendingMachineJpaEntity machine, SlotState state) {
        this.machine = Objects.requireNonNull(machine, "machine must not be null");
        apply(state);
    }

    String slotCode() {
        return slotCode;
    }

    void apply(SlotState state) {
        Objects.requireNonNull(state, "state must not be null");
        slotCode = state.code().value();
        productId = state.product().id().value();
        productName = state.product().name();
        priceAmount = state.product().price().amount();
        priceCurrency = state.product().price().currency();
        quantity = state.quantity();
    }

    SlotState toState() {
        return new SlotState(
                new SlotCode(slotCode),
                new ProductSnapshot(
                        new ProductId(productId),
                        productName,
                        Money.of(priceAmount, priceCurrency)),
                quantity);
    }
}
