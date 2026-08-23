package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.Objects;

public final class SessionNotFoundException extends RuntimeException {

    private final MachineId machineId;
    private final SessionId sessionId;

    public SessionNotFoundException(MachineId machineId, SessionId sessionId) {
        super("Session " + sessionId.value() + " was not found for machine " + machineId.value());
        this.machineId = Objects.requireNonNull(machineId, "machineId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    }

    public MachineId machineId() {
        return machineId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
