package io.github.ahmasm.vending.machine.adapter.out.persistence.machine;

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
@Table(name = "session_tender")
public class SessionTenderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PurchaseSessionJpaEntity session;

    @Convert(converter = DenominationJpaConverter.class)
    @Column(name = "denomination", nullable = false)
    private Denomination denomination;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected SessionTenderJpaEntity() {}

    SessionTenderJpaEntity(
            PurchaseSessionJpaEntity session, Denomination denomination, int quantity) {
        this.session = Objects.requireNonNull(session, "session must not be null");
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
