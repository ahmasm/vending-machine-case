package io.github.ahmasm.vending.machine.domain.machine;

import java.util.Optional;

public interface VendingMachineRepository {

    Optional<VendingMachine> findById(MachineId machineId);

    Optional<VendingMachine> findForMutation(MachineId machineId);

    void save(VendingMachine machine);
}
