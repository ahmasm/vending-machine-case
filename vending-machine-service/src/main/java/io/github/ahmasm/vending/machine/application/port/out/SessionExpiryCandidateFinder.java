package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.application.session.SessionExpiryCandidate;
import java.time.Instant;
import java.util.List;

public interface SessionExpiryCandidateFinder {

    List<SessionExpiryCandidate> findCandidates(Instant inactiveSince, int limit);
}
