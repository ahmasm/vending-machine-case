package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.application.command.MachineNotFoundException;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.application.port.out.VendingMachineRepository;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalValidatedMoneyExecutor {

    private final VendingMachineRepository machineRepository;
    private final ProcessedCommandStore processedCommandStore;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionalValidatedMoneyExecutor(
            VendingMachineRepository machineRepository,
            ProcessedCommandStore processedCommandStore,
            ApplicationEventPublisher eventPublisher) {
        this.machineRepository = Objects.requireNonNull(
                machineRepository, "machineRepository must not be null");
        this.processedCommandStore = Objects.requireNonNull(
                processedCommandStore, "processedCommandStore must not be null");
        this.eventPublisher =
                Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Transactional
    public InsertMoneyResult execute(
            InsertMoneyCommand command,
            Denomination validatedDenomination,
            String requestHash,
            Instant acceptedAt) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(validatedDenomination, "validatedDenomination must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        var claimed = processedCommandStore.claim(
                command.machineId(), command.idempotencyKey(), requestHash);
        if (!claimed) {
            return processedCommandStore
                    .find(command.machineId(), command.idempotencyKey())
                    .map(stored -> InsertMoneyService.replay(command, requestHash, stored))
                    .orElseThrow(() -> new IllegalStateException(
                            "Concurrent processed command could not be replayed"));
        }

        var machine = machineRepository
                .findForMutation(command.machineId())
                .orElseThrow(() -> new MachineNotFoundException(command.machineId()));
        var balance = machine.acceptMoney(
                command.sessionId(), validatedDenomination, acceptedAt);
        var result = new InsertMoneyResult(balance);
        machineRepository.save(machine);
        machine.releaseEvents().forEach(eventPublisher::publishEvent);
        processedCommandStore.complete(
                command.machineId(),
                command.idempotencyKey(),
                requestHash,
                result,
                acceptedAt);
        return result;
    }
}
