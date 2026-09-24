package com.jedts.theeconomist.citizen.trade;

public record CitizenPriceSnapshot(long calculatedAtTick, long version, int wheatPrice, int seedPrice, int hoePrice) {
    public CitizenPriceSnapshot {
        if (calculatedAtTick < 0 || version < 0 || wheatPrice < 1 || seedPrice < 1 || hoePrice < 1)
            throw new IllegalArgumentException("invalid Citizen price snapshot");
    }
}
