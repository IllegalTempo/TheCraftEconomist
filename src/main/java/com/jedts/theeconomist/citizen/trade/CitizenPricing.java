package com.jedts.theeconomist.citizen.trade;

public final class CitizenPricing {
    public static final long UPDATE_TICKS = 6000;

    private CitizenPricing() { }

    public static CitizenPriceSnapshot update(CitizenPriceSnapshot old, long now, int sellableWheat) {
        if (now - old.calculatedAtTick() < UPDATE_TICKS) return old;
        int wheat = Math.max(2, Math.min(6, 4 + (int) Math.ceil((16 - Math.max(0, sellableWheat)) / 8.0)));
        return new CitizenPriceSnapshot(now, old.version() + 1, wheat, 1, 8);
    }
}
