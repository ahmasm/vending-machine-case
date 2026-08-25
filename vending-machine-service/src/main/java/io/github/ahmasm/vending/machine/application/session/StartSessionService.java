package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.application.command.CanonicalCommandFingerprint;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.command.MachineNotFoundException;
import io.github.ahmasm.vending.machine.application.command.StartSessionResult;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.application.port.out.SessionIdGenerator;
import io.github.ahmasm.vending.machine.application.port.out.StoredProcessedCommand;
import io.github.ahmasm.vending.machine.application.port.out.VendingMachineRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartSessionService {

    private static final String COMMAND_TYPE = "START_SESSION";

    private final VendingMachineRepository machineRepository;
    private final ProcessedCommandStore processedCommandStore;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionIdGenerator sessionIdGenerator;
    private final Clock clock;

    public StartSessionService(
            VendingMachineRepository machineRepository,
            ProcessedCommandStore processedCommandStore,
            ApplicationEventPublisher eventPublisher,
            SessionIdGenerator sessionIdGenerator,
            Clock clock) {
        this.machineRepository = Objects.requireNonNull(
                machineRepository, "machineRepository must not be null");
        this.processedCommandStore = Objects.requireNonNull(
                processedCommandStore, "processedCommandStore must not be null");
        this.eventPublisher =
                Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.sessionIdGenerator = Objects.requireNonNull(
                sessionIdGenerator, "sessionIdGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public StartSessionResult handle(StartSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var requestHash = CanonicalCommandFingerprint.hash(
                COMMAND_TYPE, command.machineId().value());

        var stored = processedCommandStore.find(command.machineId(), command.idempotencyKey());
        if (stored.isPresent()) {
            return replay(command, requestHash, stored.orElseThrow());
        }

        var claimed = processedCommandStore.claim(
                command.machineId(), command.idempotencyKey(), requestHash);
        if (!claimed) {
            return processedCommandStore
                    .find(command.machineId(), command.idempotencyKey())
                    .map(result -> replay(command, requestHash, result))
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent processed command could not be replayed"));
        }

        var machine = machineRepository
                .findForMutation(command.machineId())
                .orElseThrow(() -> new MachineNotFoundException(command.machineId()));
        var startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var sessionId = sessionIdGenerator.next();
        machine.startSession(sessionId, startedAt);
        var result = new StartSessionResult(sessionId, startedAt);

        machineRepository.save(machine);
        machine.releaseEvents().forEach(eventPublisher::publishEvent);
        processedCommandStore.complete(
                command.machineId(),
                command.idempotencyKey(),
                requestHash,
                result,
                startedAt);
        return result;
    }

    private static StartSessionResult replay(
            StartSessionCommand command,
            String requestHash,
            StoredProcessedCommand stored) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(command.machineId());
        }
        var result = stored.result().orElseThrow(() -> new IllegalStateException(
                "Processed command has no completed application result"));
        if (result instanceof StartSessionResult startSessionResult) {
            return startSessionResult;
        }
        throw new IllegalStateException("Processed command result does not match START_SESSION");
    }
}
