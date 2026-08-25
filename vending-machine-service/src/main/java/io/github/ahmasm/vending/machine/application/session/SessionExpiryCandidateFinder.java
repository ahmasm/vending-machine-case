package io.github.ahmasm.vending.machine.application.session;

import java.time.Instant;
import java.util.List;

public interface SessionExpiryCandidateFinder {

    List<SessionExpiryCandidate> findCandidates(Instant inactiveSince, int limit);
}
