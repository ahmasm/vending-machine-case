package io.github.ahmasm.vending.machine.persistence.purchase;

import io.github.ahmasm.vending.machine.application.purchase.PurchaseStore;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPurchaseStore implements PurchaseStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void save(Purchase purchase) {
        Objects.requireNonNull(purchase, "purchase must not be null");
        jdbcTemplate.update(
                """
                insert into purchase (
                    transaction_id,
                    machine_id,
                    session_id,
                    slot_code,
                    product_id,
                    product_name,
                    price_amount,
                    currency,
                    inserted_amount,
                    completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                purchase.transactionId().value(),
                purchase.machineId().value(),
                purchase.sessionId().value(),
                purchase.slotCode().value(),
                purchase.product().id().value(),
                purchase.product().name(),
                purchase.product().price().amount(),
                purchase.product().price().currency().name(),
                purchase.insertedAmount().amount(),
                purchase.completedAt().atOffset(ZoneOffset.UTC));

        for (var denomination : Denomination.values()) {
            var quantity = purchase.change().quantityOf(denomination);
            if (quantity > 0) {
                jdbcTemplate.update(
                        """
                        insert into purchase_change (transaction_id, denomination, quantity)
                        values (?, ?, ?)
                        """,
                        purchase.transactionId().value(),
                        denomination.value().amount(),
                        quantity);
            }
        }
    }

    @Override
    public Optional<Purchase> findById(MachineId machineId, TransactionId transactionId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        ResultSetExtractor<Optional<Purchase>> extractor = JdbcPurchaseStore::extractPurchase;
        return jdbcTemplate.query(
                """
                select
                    purchase.transaction_id,
                    purchase.machine_id,
                    purchase.session_id,
                    purchase.slot_code,
                    purchase.product_id,
                    purchase.product_name,
                    purchase.price_amount,
                    purchase.currency,
                    purchase.inserted_amount,
                    purchase.completed_at,
                    purchase_change.denomination,
                    purchase_change.quantity
                from purchase
                left join purchase_change
                    on purchase_change.transaction_id = purchase.transaction_id
                where purchase.machine_id = ? and purchase.transaction_id = ?
                order by purchase_change.denomination
                """,
                extractor,
                machineId.value(),
                transactionId.value());
    }

    private static Optional<Purchase> extractPurchase(ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }

        var transactionId = new TransactionId(resultSet.getString("transaction_id"));
        var machineId = new MachineId(resultSet.getString("machine_id"));
        var sessionId = new SessionId(resultSet.getString("session_id"));
        var slotCode = new SlotCode(resultSet.getString("slot_code"));
        var productId = new ProductId(resultSet.getString("product_id"));
        var productName = resultSet.getString("product_name");
        var currency = Currency.valueOf(resultSet.getString("currency"));
        var price = Money.of(resultSet.getLong("price_amount"), currency);
        var insertedAmount = Money.of(resultSet.getLong("inserted_amount"), currency);
        var completedAt = resultSet.getObject("completed_at", OffsetDateTime.class).toInstant();
        var change = new EnumMap<Denomination, Integer>(Denomination.class);
        do {
            var denominationValue = (Number) resultSet.getObject("denomination");
            if (denominationValue != null) {
                var denomination =
                        Denomination.from(Money.of(denominationValue.longValue(), currency));
                change.put(denomination, resultSet.getInt("quantity"));
            }
        } while (resultSet.next());

        return Optional.of(new Purchase(
                transactionId,
                machineId,
                sessionId,
                slotCode,
                new ProductSnapshot(productId, productName, price),
                insertedAmount,
                CashComposition.of(change),
                completedAt));
    }
}
