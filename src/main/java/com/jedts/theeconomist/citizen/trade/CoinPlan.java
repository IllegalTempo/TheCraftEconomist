package com.jedts.theeconomist.citizen.trade;

import java.util.Objects;

public record CoinPlan(CoinStock payment, CoinStock change) {
    public CoinPlan {
        Objects.requireNonNull(payment, "payment");
        Objects.requireNonNull(change, "change");
    }
}
