package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.SlotState;
import java.util.List;
import java.util.Optional;

public interface ProductCatalogReader {

    Optional<List<SlotState>> findSlots(MachineId machineId);
}
