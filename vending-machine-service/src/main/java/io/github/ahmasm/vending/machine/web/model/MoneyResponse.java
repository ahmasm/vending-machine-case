package io.github.ahmasm.vending.machine.web.model;

import io.github.ahmasm.vending.machine.domain.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(name = "Money")
public record MoneyResponse(
        @Schema(example = "10") long amount,
        @Schema(example = "UNIT") CurrencyCode currency) {

    public MoneyResponse {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static MoneyResponse from(Money money) {
        Objects.requireNonNull(money, "money must not be null");
        return new MoneyResponse(
                money.amount(), CurrencyCode.valueOf(money.currency().name()));
    }
}
