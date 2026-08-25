package io.github.ahmasm.vending.machine.adapter.in.scheduling;

import io.github.ahmasm.vending.machine.application.session.SessionExpiryRecovery;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "vending.session-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SessionExpiryScheduler {

    private final SessionExpiryRecovery recovery;

    public SessionExpiryScheduler(SessionExpiryRecovery recovery) {
        this.recovery = Objects.requireNonNull(recovery, "recovery must not be null");
    }

    @Scheduled(
            fixedDelayString = "${vending.session-expiry.scan-interval}",
            initialDelayString = "${vending.session-expiry.initial-delay}")
    public void recoverExpiredSessions() {
        recovery.runOnce();
    }
}
