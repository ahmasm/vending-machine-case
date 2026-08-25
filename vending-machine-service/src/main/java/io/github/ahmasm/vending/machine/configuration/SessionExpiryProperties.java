package io.github.ahmasm.vending.machine.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vending.session-expiry")
public record SessionExpiryProperties(
        boolean enabled,
        Duration inactivityTimeout,
        Duration scanInterval,
        Duration initialDelay,
        int batchSize) {

    public SessionExpiryProperties {
        inactivityTimeout = requirePositive(inactivityTimeout, "inactivityTimeout");
        scanInterval = requirePositive(scanInterval, "scanInterval");
        initialDelay = Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
