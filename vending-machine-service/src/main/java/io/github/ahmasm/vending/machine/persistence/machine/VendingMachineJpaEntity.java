package io.github.ahmasm.vending.machine.persistence.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachineState;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "vending_machine")
public class VendingMachineJpaEntity {

    @Id
    @Column(name = "machine_id", nullable = false, columnDefinition = "text")
    private String machineId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(
            mappedBy = "machine",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<MachineSlotJpaEntity> slots = new ArrayList<>();

    @OneToMany(
            mappedBy = "machine",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<CashInventoryJpaEntity> cashInventory = new ArrayList<>();

    protected VendingMachineJpaEntity() {}

    VendingMachineJpaEntity(VendingMachineState state) {
        machineId = state.id().value();
        apply(state);
    }

    long version() {
        return version;
    }

    String machineId() {
        return machineId;
    }

    void apply(VendingMachineState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (!machineId.equals(state.id().value())) {
            throw new IllegalArgumentException("Cannot change persisted machine identity");
        }
        synchronizeSlots(state);
        synchronizeCash(state.cashInventory());
    }

    VendingMachineState toState(Optional<PurchaseSessionState> currentSession) {
        Objects.requireNonNull(currentSession, "currentSession must not be null");
        var quantities = new EnumMap<Denomination, Integer>(Denomination.class);
        for (var entry : cashInventory) {
            quantities.put(entry.denomination(), entry.quantity());
        }
        var slotStates = slots.stream()
                .map(MachineSlotJpaEntity::toState)
                .sorted(Comparator.comparing(slot -> slot.code().value()))
                .toList();

        return new VendingMachineState(
                new MachineId(machineId),
                CashComposition.of(quantities),
                slotStates,
                currentSession);
    }

    private void synchronizeSlots(VendingMachineState state) {
        slots.removeIf(existing -> state.slots().stream()
                .noneMatch(slot -> slot.code().value().equals(existing.slotCode())));
        for (var slot : state.slots()) {
            slots.stream()
                    .filter(existing -> existing.slotCode().equals(slot.code().value()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.apply(slot),
                            () -> slots.add(new MachineSlotJpaEntity(this, slot)));
        }
    }

    private void synchronizeCash(CashComposition state) {
        cashInventory.removeIf(entry -> state.quantityOf(entry.denomination()) == 0);
        for (var denomination : Denomination.values()) {
            var quantity = state.quantityOf(denomination);
            if (quantity == 0) {
                continue;
            }
            cashInventory.stream()
                    .filter(entry -> entry.denomination() == denomination)
                    .findFirst()
                    .ifPresentOrElse(
                            entry -> entry.quantity(quantity),
                            () -> cashInventory.add(
                                    new CashInventoryJpaEntity(this, denomination, quantity)));
        }
    }
}
