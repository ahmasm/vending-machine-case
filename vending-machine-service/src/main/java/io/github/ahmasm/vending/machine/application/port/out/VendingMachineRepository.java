package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.MachineId;
import io.github.ahmasm.vending.machine.domain.machine.VendingMachine;
import java.util.Optional;

public interface VendingMachineRepository {

    Optional<VendingMachine> findById(MachineId machineId);

    Optional<VendingMachine> findForMutation(MachineId machineId);

    void save(VendingMachine machine);
}
