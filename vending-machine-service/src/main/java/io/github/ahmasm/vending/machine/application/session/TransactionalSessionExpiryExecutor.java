package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.application.port.out.VendingMachineRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalSessionExpiryExecutor {

    private final VendingMachineRepository machineRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionalSessionExpiryExecutor(
            VendingMachineRepository machineRepository,
            ApplicationEventPublisher eventPublisher) {
        this.machineRepository = Objects.requireNonNull(
                machineRepository, "machineRepository must not be null");
        this.eventPublisher =
                Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Transactional
    public boolean expire(
            SessionExpiryCandidate candidate, Instant checkedAt, Duration inactivityTimeout) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        var machine = machineRepository.findForMutation(candidate.machineId()).orElse(null);
        if (machine == null) {
            return false;
        }

        machine.expireSession(candidate.sessionId(), checkedAt, inactivityTimeout);
        machineRepository.save(machine);
        machine.releaseEvents().forEach(eventPublisher::publishEvent);
        return true;
    }
}
