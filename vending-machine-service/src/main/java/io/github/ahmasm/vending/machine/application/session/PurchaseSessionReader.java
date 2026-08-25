package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.PurchaseSessionState;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.Optional;

public interface PurchaseSessionReader {

    Optional<PurchaseSessionState> findById(MachineId machineId, SessionId sessionId);
}
