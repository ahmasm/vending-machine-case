package io.github.ahmasm.vending.machine.application.money;

import io.github.ahmasm.vending.machine.application.command.CanonicalCommandFingerprint;
import io.github.ahmasm.vending.machine.application.command.IdempotencyKeyReusedException;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyCommand;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidation;
import io.github.ahmasm.vending.machine.application.port.out.CurrencyValidator;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.application.port.out.StoredProcessedCommand;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class InsertMoneyService {

    private static final String COMMAND_TYPE = "INSERT_MONEY";

    private final CurrencyValidator currencyValidator;
    private final ProcessedCommandStore processedCommandStore;
    private final TransactionalValidatedMoneyExecutor validatedMoneyExecutor;
    private final Clock clock;

    public InsertMoneyService(
            CurrencyValidator currencyValidator,
            ProcessedCommandStore processedCommandStore,
            TransactionalValidatedMoneyExecutor validatedMoneyExecutor,
            Clock clock) {
        this.currencyValidator =
                Objects.requireNonNull(currencyValidator, "currencyValidator must not be null");
        this.processedCommandStore = Objects.requireNonNull(
                processedCommandStore, "processedCommandStore must not be null");
        this.validatedMoneyExecutor = Objects.requireNonNull(
                validatedMoneyExecutor, "validatedMoneyExecutor must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public InsertMoneyResult handle(InsertMoneyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var requestHash = requestHash(command);
        var stored = processedCommandStore.find(command.machineId(), command.idempotencyKey());
        if (stored.isPresent()) {
            return replay(command, requestHash, stored.orElseThrow());
        }

        var validation = Objects.requireNonNull(
                currencyValidator.validate(command.machineId(), command.denomination()),
                "currency validation must not be null");

        return switch (validation) {
            case ACCEPTED ->
                validatedMoneyExecutor.execute(command, requestHash, clock.instant());
            case REJECTED -> throw new CurrencyRejectedException(
                    command.machineId(), command.denomination());
            case UNAVAILABLE -> throw new CurrencyValidationUnavailableException(
                    command.machineId(), command.denomination());
        };
    }

    static InsertMoneyResult replay(
            InsertMoneyCommand command,
            String requestHash,
            StoredProcessedCommand stored) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(command.machineId());
        }
        var result = stored.result().orElseThrow(() -> new IllegalStateException(
                "Processed command has no completed application result"));
        if (result instanceof InsertMoneyResult insertMoneyResult) {
            return insertMoneyResult;
        }
        throw new IllegalStateException("Processed command result does not match INSERT_MONEY");
    }

    private static String requestHash(InsertMoneyCommand command) {
        return CanonicalCommandFingerprint.hash(
                COMMAND_TYPE,
                command.machineId().value(),
                command.sessionId().value(),
                Long.toString(command.denomination().value().amount()),
                command.denomination().value().currency().name());
    }
}
