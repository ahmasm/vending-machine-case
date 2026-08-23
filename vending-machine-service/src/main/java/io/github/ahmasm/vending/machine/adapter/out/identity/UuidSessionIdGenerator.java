package io.github.ahmasm.vending.machine.adapter.out.identity;

import io.github.ahmasm.vending.machine.application.port.out.SessionIdGenerator;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class UuidSessionIdGenerator implements SessionIdGenerator {

    @Override
    public SessionId next() {
        return new SessionId(UUID.randomUUID().toString());
    }
}
