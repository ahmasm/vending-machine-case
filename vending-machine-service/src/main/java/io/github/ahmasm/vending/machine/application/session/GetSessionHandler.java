package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.application.port.out.PurchaseSessionReader;
import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class GetSessionHandler {

    private final PurchaseSessionReader sessionReader;

    public GetSessionHandler(PurchaseSessionReader sessionReader) {
        this.sessionReader = Objects.requireNonNull(sessionReader, "sessionReader must not be null");
    }

    public PurchaseSessionState handle(MachineId machineId, SessionId sessionId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return sessionReader
                .findById(machineId, sessionId)
                .orElseThrow(() -> new SessionNotFoundException(machineId, sessionId));
    }
}
