package io.github.ahmasm.vending.machine.adapter.out.persistence.session;

import io.github.ahmasm.vending.machine.application.port.out.SessionExpiryCandidateFinder;
import io.github.ahmasm.vending.machine.application.session.SessionExpiryCandidate;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionExpiryCandidateFinder implements SessionExpiryCandidateFinder {

    private static final String FIND_CANDIDATES_SQL = """
            select machine_id, session_id
            from purchase_session
            where status = 'ACTIVE'
              and last_activity_at <= ?
            order by last_activity_at, machine_id, session_id
            limit ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionExpiryCandidateFinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<SessionExpiryCandidate> findCandidates(Instant inactiveSince, int limit) {
        Objects.requireNonNull(inactiveSince, "inactiveSince must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return List.copyOf(jdbcTemplate.query(
                FIND_CANDIDATES_SQL,
                (resultSet, rowNumber) -> new SessionExpiryCandidate(
                        new MachineId(resultSet.getString("machine_id")),
                        new SessionId(resultSet.getString("session_id"))),
                inactiveSince.atOffset(ZoneOffset.UTC),
                limit));
    }
}
