package io.github.ahmasm.vending.machine.adapter.out.persistence.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ahmasm.vending.machine.application.port.in.IdempotencyKey;
import io.github.ahmasm.vending.machine.application.port.in.InsertMoneyResult;
import io.github.ahmasm.vending.machine.application.port.in.ProcessedCommandResult;
import io.github.ahmasm.vending.machine.application.port.in.RefundResult;
import io.github.ahmasm.vending.machine.application.port.in.SelectProductResult;
import io.github.ahmasm.vending.machine.application.port.in.StartSessionResult;
import io.github.ahmasm.vending.machine.application.port.out.ProcessedCommandStore;
import io.github.ahmasm.vending.machine.application.port.out.StoredProcessedCommand;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.Purchase;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import io.github.ahmasm.vending.machine.domain.money.Currency;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProcessedCommandStore implements ProcessedCommandStore {

    private static final String MONEY_ACCEPTED_RESULT_CODE = "MONEY_ACCEPTED";
    private static final String PURCHASE_COMPLETED_RESULT_CODE = "PURCHASE_COMPLETED";
    private static final String REFUND_COMPLETED_RESULT_CODE = "REFUND_COMPLETED";
    private static final String SESSION_STARTED_RESULT_CODE = "SESSION_STARTED";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcProcessedCommandStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<StoredProcessedCommand> find(
            MachineId machineId, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        return jdbcTemplate
                .query(
                        """
                        select request_hash, result_code, result_payload::text
                        from processed_command
                        where machine_id = ? and idempotency_key = ?
                        """,
                        (resultSet, rowNumber) -> mapStoredCommand(
                                resultSet.getString("request_hash"),
                                resultSet.getString("result_code"),
                                resultSet.getString("result_payload")),
                        machineId.value(),
                        idempotencyKey.value())
                .stream()
                .findFirst();
    }

    @Override
    public boolean claim(
            MachineId machineId, IdempotencyKey idempotencyKey, String requestHash) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        return jdbcTemplate.update(
                        """
                        insert into processed_command (
                            machine_id, idempotency_key, request_hash
                        ) values (?, ?, ?)
                        on conflict (machine_id, idempotency_key) do nothing
                        """,
                        machineId.value(),
                        idempotencyKey.value(),
                        requestHash)
                == 1;
    }

    @Override
    public void complete(
            MachineId machineId,
            IdempotencyKey idempotencyKey,
            String requestHash,
            ProcessedCommandResult result,
            Instant completedAt) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        var serializedResult = serialize(result);
        var updated = jdbcTemplate.update(
                """
                update processed_command
                set result_code = ?, result_payload = cast(? as jsonb), completed_at = ?
                where machine_id = ?
                  and idempotency_key = ?
                  and request_hash = ?
                  and result_code is null
                """,
                serializedResult.code(),
                serializedResult.payload(),
                completedAt.atOffset(ZoneOffset.UTC),
                machineId.value(),
                idempotencyKey.value(),
                requestHash);
        if (updated != 1) {
            throw new IllegalStateException("Processed command claim could not be completed");
        }
    }

    private StoredProcessedCommand mapStoredCommand(
            String requestHash, String resultCode, String resultPayload) {
        if (resultCode == null) {
            return new StoredProcessedCommand(requestHash, Optional.empty());
        }
        var result = switch (resultCode) {
            case MONEY_ACCEPTED_RESULT_CODE -> {
                var payload = readJson(resultPayload, InsertMoneyResultPayload.class);
                yield new InsertMoneyResult(
                        Money.of(payload.balanceAmount(), payload.currency()));
            }
            case PURCHASE_COMPLETED_RESULT_CODE -> {
                var payload = readJson(resultPayload, SelectProductResultPayload.class);
                yield new SelectProductResult(new Purchase(
                        new TransactionId(payload.transactionId()),
                        new MachineId(payload.machineId()),
                        new SessionId(payload.sessionId()),
                        new SlotCode(payload.slotCode()),
                        new ProductSnapshot(
                                new ProductId(payload.productId()),
                                payload.productName(),
                                Money.of(payload.priceAmount(), payload.currency())),
                        Money.of(payload.insertedAmount(), payload.currency()),
                        CashComposition.of(payload.changeComposition()),
                        payload.completedAt()));
            }
            case REFUND_COMPLETED_RESULT_CODE -> {
                var payload = readJson(resultPayload, RefundResultPayload.class);
                yield new RefundResult(CashComposition.of(payload.returnedComposition()));
            }
            case SESSION_STARTED_RESULT_CODE -> {
                var payload = readJson(resultPayload, StartSessionResultPayload.class);
                yield new StartSessionResult(
                        new SessionId(payload.sessionId()), payload.startedAt());
            }
            default -> throw new IllegalStateException(
                    "Unsupported processed command result code " + resultCode);
        };
        return new StoredProcessedCommand(requestHash, Optional.of(result));
    }

    private SerializedResult serialize(ProcessedCommandResult result) {
        return switch (result) {
            case InsertMoneyResult insertMoneyResult -> new SerializedResult(
                    MONEY_ACCEPTED_RESULT_CODE,
                    writeJson(new InsertMoneyResultPayload(
                            insertMoneyResult.balance().amount(),
                            insertMoneyResult.balance().currency())));
            case SelectProductResult selectProductResult -> {
                var purchase = selectProductResult.purchase();
                yield new SerializedResult(
                        PURCHASE_COMPLETED_RESULT_CODE,
                        writeJson(new SelectProductResultPayload(
                                purchase.transactionId().value(),
                                purchase.machineId().value(),
                                purchase.sessionId().value(),
                                purchase.slotCode().value(),
                                purchase.product().id().value(),
                                purchase.product().name(),
                                purchase.product().price().amount(),
                                purchase.product().price().currency(),
                                purchase.insertedAmount().amount(),
                                quantitiesOf(purchase.change()),
                                purchase.completedAt())));
            }
            case RefundResult refundResult -> new SerializedResult(
                    REFUND_COMPLETED_RESULT_CODE,
                    writeJson(new RefundResultPayload(
                            quantitiesOf(refundResult.returnedCash()))));
            case StartSessionResult startSessionResult -> new SerializedResult(
                    SESSION_STARTED_RESULT_CODE,
                    writeJson(new StartSessionResultPayload(
                            startSessionResult.sessionId().value(),
                            startSessionResult.startedAt())));
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Processed command result could not be serialized", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Processed command result could not be read", exception);
        }
    }

    private static Map<Denomination, Integer> quantitiesOf(CashComposition composition) {
        var quantities = new EnumMap<Denomination, Integer>(Denomination.class);
        for (var denomination : Denomination.values()) {
            var quantity = composition.quantityOf(denomination);
            if (quantity > 0) {
                quantities.put(denomination, quantity);
            }
        }
        return Map.copyOf(quantities);
    }

    private record InsertMoneyResultPayload(long balanceAmount, Currency currency) {}

    private record SelectProductResultPayload(
            String transactionId,
            String machineId,
            String sessionId,
            String slotCode,
            String productId,
            String productName,
            long priceAmount,
            Currency currency,
            long insertedAmount,
            Map<Denomination, Integer> changeComposition,
            Instant completedAt) {

        private SelectProductResultPayload {
            changeComposition = Map.copyOf(changeComposition);
        }
    }

    private record RefundResultPayload(Map<Denomination, Integer> returnedComposition) {

        private RefundResultPayload {
            returnedComposition = Map.copyOf(returnedComposition);
        }
    }

    private record StartSessionResultPayload(String sessionId, Instant startedAt) {}

    private record SerializedResult(String code, String payload) {}
}
