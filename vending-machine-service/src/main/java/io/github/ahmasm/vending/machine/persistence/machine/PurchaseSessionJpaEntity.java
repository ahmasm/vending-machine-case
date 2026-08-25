package io.github.ahmasm.vending.machine.persistence.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SessionStatus;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "purchase_session")
public class PurchaseSessionJpaEntity {

    @Id
    @Column(name = "session_id", nullable = false, columnDefinition = "text")
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private VendingMachineJpaEntity machine;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @OneToMany(
            mappedBy = "session",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<SessionTenderJpaEntity> tender = new ArrayList<>();

    protected PurchaseSessionJpaEntity() {}

    PurchaseSessionJpaEntity(VendingMachineJpaEntity machine, PurchaseSessionState state) {
        this.machine = Objects.requireNonNull(machine, "machine must not be null");
        sessionId = state.id().value();
        apply(state);
    }

    String machineId() {
        return machine.machineId();
    }

    void apply(PurchaseSessionState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (!sessionId.equals(state.id().value())) {
            throw new IllegalArgumentException("Cannot change persisted session identity");
        }
        status = state.status();
        startedAt = state.startedAt();
        lastActivityAt = state.lastActivityAt();
        synchronizeTender(state.escrow());
    }

    PurchaseSessionState toState() {
        var quantities = new EnumMap<Denomination, Integer>(Denomination.class);
        for (var entry : tender) {
            quantities.put(entry.denomination(), entry.quantity());
        }
        return new PurchaseSessionState(
                new SessionId(sessionId),
                status,
                CashComposition.of(quantities),
                startedAt,
                lastActivityAt);
    }

    private void synchronizeTender(CashComposition escrow) {
        tender.removeIf(entry -> escrow.quantityOf(entry.denomination()) == 0);
        for (var denomination : Denomination.values()) {
            var quantity = escrow.quantityOf(denomination);
            if (quantity == 0) {
                continue;
            }
            tender.stream()
                    .filter(entry -> entry.denomination() == denomination)
                    .findFirst()
                    .ifPresentOrElse(
                            entry -> entry.quantity(quantity),
                            () -> tender.add(
                                    new SessionTenderJpaEntity(this, denomination, quantity)));
        }
    }
}
