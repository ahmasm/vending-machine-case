package io.github.ahmasm.vending.machine.persistence.machine;

import io.github.ahmasm.vending.machine.domain.money.Denomination;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "cash_inventory")
public class CashInventoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private VendingMachineJpaEntity machine;

    @Convert(converter = DenominationJpaConverter.class)
    @Column(name = "denomination", nullable = false)
    private Denomination denomination;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected CashInventoryJpaEntity() {}

    CashInventoryJpaEntity(
            VendingMachineJpaEntity machine, Denomination denomination, int quantity) {
        this.machine = Objects.requireNonNull(machine, "machine must not be null");
        this.denomination =
                Objects.requireNonNull(denomination, "denomination must not be null");
        this.quantity = quantity;
    }

    Denomination denomination() {
        return denomination;
    }

    int quantity() {
        return quantity;
    }

    void quantity(int quantity) {
        this.quantity = quantity;
    }
}
