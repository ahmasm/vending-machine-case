package io.github.ahmasm.vending.machine.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Vending Machine API",
                        version = "v1",
                        description = "Commands exposed by the authoritative vending machine service"))
public class OpenApiConfiguration {}
