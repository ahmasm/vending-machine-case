package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.Objects;

public record SessionExpiryCandidate(MachineId machineId, SessionId sessionId) {

    public SessionExpiryCandidate {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
