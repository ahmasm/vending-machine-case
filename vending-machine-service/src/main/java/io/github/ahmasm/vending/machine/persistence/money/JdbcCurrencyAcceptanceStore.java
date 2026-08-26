package io.github.ahmasm.vending.machine.persistence.money;

import io.github.ahmasm.vending.machine.application.command.CanonicalCommandFingerprint;
import io.github.ahmasm.vending.machine.application.money.CurrencyAcceptanceStore;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCurrencyAcceptanceStore implements CurrencyAcceptanceStore {

    private static final String FINGERPRINT_TYPE = "CURRENCY_ACCEPTANCE";

    private final JdbcTemplate jdbcTemplate;

    public JdbcCurrencyAcceptanceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public boolean claim(MachineId machineId, String validatorReference, Instant consumedAt) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(validatorReference, "validatorReference must not be null");
        Objects.requireNonNull(consumedAt, "consumedAt must not be null");
        var referenceHash = CanonicalCommandFingerprint.hash(
                FINGERPRINT_TYPE, machineId.value(), validatorReference);
        return jdbcTemplate.update(
                        """
                        insert into currency_acceptance (
                            machine_id, validator_reference_hash, consumed_at
                        ) values (?, ?, ?)
                        on conflict (machine_id, validator_reference_hash) do nothing
                        """,
                        machineId.value(),
                        referenceHash,
                        consumedAt.atOffset(ZoneOffset.UTC))
                == 1;
    }
}
