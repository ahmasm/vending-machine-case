package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.application.port.out.SessionExpiryCandidateFinder;
import io.github.ahmasm.vending.machine.configuration.SessionExpiryProperties;
import io.github.ahmasm.vending.machine.domain.machine.ActiveSessionNotFoundException;
import io.github.ahmasm.vending.machine.domain.machine.SessionNotExpiredException;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionExpiryRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionExpiryRecovery.class);

    private final SessionExpiryCandidateFinder candidateFinder;
    private final TransactionalSessionExpiryExecutor expiryExecutor;
    private final SessionExpiryProperties properties;
    private final Clock clock;

    public SessionExpiryRecovery(
            SessionExpiryCandidateFinder candidateFinder,
            TransactionalSessionExpiryExecutor expiryExecutor,
            SessionExpiryProperties properties,
            Clock clock) {
        this.candidateFinder =
                Objects.requireNonNull(candidateFinder, "candidateFinder must not be null");
        this.expiryExecutor =
                Objects.requireNonNull(expiryExecutor, "expiryExecutor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public int runOnce() {
        var checkedAt = clock.instant();
        var inactivityTimeout = properties.inactivityTimeout();
        var inactiveSince = checkedAt.minus(inactivityTimeout);
        var candidates = candidateFinder.findCandidates(inactiveSince, properties.batchSize());
        var expiredCount = 0;
        for (var candidate : candidates) {
            try {
                if (expiryExecutor.expire(candidate, checkedAt, inactivityTimeout)) {
                    expiredCount++;
                }
            } catch (ActiveSessionNotFoundException | SessionNotExpiredException staleCandidate) {
                // Candidate scans are approximate; transaction rollback preserves the root version.
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Could not expire session candidate machineId={} sessionId={}",
                        candidate.machineId().value(),
                        candidate.sessionId().value(),
                        failure);
            }
        }
        return expiredCount;
    }
}
