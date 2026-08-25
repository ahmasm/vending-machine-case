package io.github.ahmasm.vending.machine.persistence.session;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;

import io.github.ahmasm.vending.machine.application.session.PurchaseSessionReader;
import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.SessionStatus;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPurchaseSessionReader implements PurchaseSessionReader {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseSessionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<PurchaseSessionState> findById(MachineId machineId, SessionId sessionId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        ResultSetExtractor<Optional<PurchaseSessionState>> extractor =
                JdbcPurchaseSessionReader::extractSession;
        return jdbcTemplate.query(
                """
                select
                    session.session_id,
                    session.status,
                    session.started_at,
                    session.last_activity_at,
                    tender.denomination,
                    tender.quantity
                from purchase_session session
                left join session_tender tender on tender.session_id = session.session_id
                where session.machine_id = ? and session.session_id = ?
                order by tender.denomination
                """,
                extractor,
                machineId.value(),
                sessionId.value());
    }

    private static Optional<PurchaseSessionState> extractSession(ResultSet resultSet)
            throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }

        var sessionId = new SessionId(resultSet.getString("session_id"));
        var status = SessionStatus.valueOf(resultSet.getString("status"));
        var startedAt = resultSet.getObject("started_at", OffsetDateTime.class).toInstant();
        var lastActivityAt = resultSet
                .getObject("last_activity_at", OffsetDateTime.class)
                .toInstant();
        var tender = new EnumMap<Denomination, Integer>(Denomination.class);
        do {
            var denominationValue = (Number) resultSet.getObject("denomination");
            if (denominationValue != null) {
                var denomination = Denomination.from(Money.of(denominationValue.longValue(), UNIT));
                tender.put(denomination, resultSet.getInt("quantity"));
            }
        } while (resultSet.next());

        return Optional.of(new PurchaseSessionState(
                sessionId,
                status,
                CashComposition.of(tender),
                startedAt,
                lastActivityAt));
    }
}
