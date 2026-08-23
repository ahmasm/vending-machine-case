package io.github.ahmasm.vending.machine.adapter.out.persistence.product;

import io.github.ahmasm.vending.machine.application.port.out.ProductCatalogReader;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.ProductId;
import io.github.ahmasm.vending.machine.domain.machine.ProductSnapshot;
import io.github.ahmasm.vending.machine.domain.machine.SlotCode;
import io.github.ahmasm.vending.machine.domain.machine.SlotState;
import io.github.ahmasm.vending.machine.domain.money.Currency;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductCatalogReader implements ProductCatalogReader {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductCatalogReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<List<SlotState>> findSlots(MachineId machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        ResultSetExtractor<Optional<List<SlotState>>> extractor =
                JdbcProductCatalogReader::extractSlots;
        return jdbcTemplate.query(
                """
                select
                    machine.machine_id,
                    slot.slot_code,
                    slot.product_id,
                    slot.product_name,
                    slot.price_amount,
                    slot.price_currency,
                    slot.quantity
                from vending_machine machine
                left join machine_slot slot on slot.machine_id = machine.machine_id
                where machine.machine_id = ?
                order by slot.slot_code
                """,
                extractor,
                machineId.value());
    }

    private static Optional<List<SlotState>> extractSlots(ResultSet resultSet)
            throws SQLException {
        var machineFound = false;
        var slots = new ArrayList<SlotState>();
        while (resultSet.next()) {
            machineFound = true;
            var slotCode = resultSet.getString("slot_code");
            if (slotCode != null) {
                slots.add(toSlot(resultSet, slotCode));
            }
        }
        return machineFound ? Optional.of(List.copyOf(slots)) : Optional.empty();
    }

    private static SlotState toSlot(ResultSet resultSet, String slotCode) throws SQLException {
        return new SlotState(
                new SlotCode(slotCode),
                new ProductSnapshot(
                        new ProductId(resultSet.getString("product_id")),
                        resultSet.getString("product_name"),
                        Money.of(
                                resultSet.getLong("price_amount"),
                                Currency.valueOf(resultSet.getString("price_currency")))),
                resultSet.getInt("quantity"));
    }
}
