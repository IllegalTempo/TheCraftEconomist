package com.jedts.theeconomist.trade.domain;

import java.util.Objects;
import java.util.UUID;

public record TradePair(UUID first, UUID second) {
    public TradePair {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) throw new IllegalArgumentException("trade participants must differ");
    }

    public static TradePair of(UUID left, UUID right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (compare(left, right) <= 0) return new TradePair(left, right);
        return new TradePair(right, left);
    }

    private static int compare(UUID left, UUID right) {
        int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return most != 0 ? most : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
