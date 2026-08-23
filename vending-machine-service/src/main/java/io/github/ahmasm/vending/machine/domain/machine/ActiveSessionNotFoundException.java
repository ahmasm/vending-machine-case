package io.github.ahmasm.vending.machine.domain.machine;

public final class ActiveSessionNotFoundException extends IllegalStateException {

    private final MachineId machineId;
    private final SessionId requestedSessionId;

    public ActiveSessionNotFoundException(MachineId machineId, SessionId requestedSessionId) {
        super("Machine " + machineId.value() + " has no active session " + requestedSessionId.value());
        this.machineId = machineId;
        this.requestedSessionId = requestedSessionId;
    }

    public MachineId machineId() {
        return machineId;
    }

    public SessionId requestedSessionId() {
        return requestedSessionId;
    }
}
