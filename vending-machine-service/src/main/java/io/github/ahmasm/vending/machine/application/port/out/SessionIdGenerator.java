package io.github.ahmasm.vending.machine.application.port.out;

import io.github.ahmasm.vending.machine.domain.machine.SessionId;

@FunctionalInterface
public interface SessionIdGenerator {

    SessionId next();
}
