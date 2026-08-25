package io.github.ahmasm.vending.machine.configuration;

import io.github.ahmasm.vending.machine.application.purchase.TransactionIdGenerator;
import io.github.ahmasm.vending.machine.application.session.SessionIdGenerator;
import io.github.ahmasm.vending.machine.domain.machine.SessionId;
import io.github.ahmasm.vending.machine.domain.machine.TransactionId;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    SessionIdGenerator sessionIdGenerator() {
        return () -> new SessionId(UUID.randomUUID().toString());
    }

    @Bean
    TransactionIdGenerator transactionIdGenerator() {
        return () -> new TransactionId(UUID.randomUUID().toString());
    }
}
