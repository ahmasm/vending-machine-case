package io.github.ahmasm.vending.machine.persistence.machine;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaVendingMachineRepository implements VendingMachineRepository {

    private final EntityManager entityManager;

    public JpaVendingMachineRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public Optional<VendingMachine> findById(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        return Optional.ofNullable(entityManager.find(
                        VendingMachineJpaEntity.class, machineId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<VendingMachine> findForMutation(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        return Optional.ofNullable(entityManager.find(
                        VendingMachineJpaEntity.class,
                        machineId.value(),
                        LockModeType.OPTIMISTIC_FORCE_INCREMENT))
                .map(this::toDomain);
    }

    @Override
    public void save(VendingMachine machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        var state = machine.snapshot();
        var entity = entityManager.find(VendingMachineJpaEntity.class, state.id().value());
        if (entity == null) {
            entity = new VendingMachineJpaEntity(state);
            entityManager.persist(entity);
        } else {
            entity.apply(state);
        }
        var persistedMachine = entity;
        state.currentSession()
                .ifPresent(session -> synchronizeSession(persistedMachine, session));
        entityManager.flush();
    }

    private VendingMachine toDomain(VendingMachineJpaEntity entity) {
        var activeSession = findActiveSession(entity.machineId());
        return VendingMachine.restore(entity.toState(activeSession));
    }

    private Optional<PurchaseSessionState> findActiveSession(String machineId) {
        return entityManager
                .createQuery(
                        """
                        select distinct session
                        from PurchaseSessionJpaEntity session
                        left join fetch session.tender
                        where session.machine.machineId = :machineId
                          and session.status = ACTIVE
                        """,
                        PurchaseSessionJpaEntity.class)
                .setParameter("machineId", machineId)
                .getResultStream()
                .findFirst()
                .map(PurchaseSessionJpaEntity::toState);
    }

    private void synchronizeSession(
            VendingMachineJpaEntity machine, PurchaseSessionState state) {
        var session = entityManager.find(
                PurchaseSessionJpaEntity.class, state.id().value());
        if (session == null) {
            entityManager.persist(new PurchaseSessionJpaEntity(machine, state));
            return;
        }
        if (!session.machineId().equals(machine.machineId())) {
            throw new IllegalStateException("Persisted session belongs to a different machine");
        }
        session.apply(state);
    }
}
