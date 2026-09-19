package com.jedts.theeconomist.currency;

import java.util.Map;
import java.util.Objects;

public final class CrownMath {
    private CrownMath() {
    }

    public static long total(Map<CrownDenomination, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        long total = 0L;
        for (Map.Entry<CrownDenomination, Integer> entry : counts.entrySet()) {
            CrownDenomination denomination = Objects.requireNonNull(entry.getKey(), "denomination");
            int count = Objects.requireNonNull(entry.getValue(), "count");
            if (count < 0) {
                throw new IllegalArgumentException("Crown counts must not be negative");
            }
            total = Math.addExact(total, Math.multiplyExact((long) denomination.unitValue(), count));
        }
        return total;
    }
}
