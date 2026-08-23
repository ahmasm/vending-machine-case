package io.github.ahmasm.vending.machine.domain.machine;

public final class ActiveSessionAlreadyExistsException extends IllegalStateException {

    private final MachineId machineId;
    private final SessionId activeSessionId;

    public ActiveSessionAlreadyExistsException(MachineId machineId, SessionId activeSessionId) {
        super("Machine " + machineId.value() + " already has active session " + activeSessionId.value());
        this.machineId = machineId;
        this.activeSessionId = activeSessionId;
    }

    public MachineId machineId() {
        return machineId;
    }

    public SessionId activeSessionId() {
        return activeSessionId;
    }
}
