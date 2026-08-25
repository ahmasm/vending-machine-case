package io.github.ahmasm.vending.machine.application.session;

import io.github.ahmasm.vending.machine.domain.machine.SessionId;

@FunctionalInterface
public interface SessionIdGenerator {

    SessionId next();
}
