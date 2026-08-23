package io.github.ahmasm.vending.machine.adapter.in.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(name = "InsertMoneyResponse")
public record InsertMoneyResponse(MoneyResponse balance) {

    public InsertMoneyResponse {
        Objects.requireNonNull(balance, "balance must not be null");
    }
}
