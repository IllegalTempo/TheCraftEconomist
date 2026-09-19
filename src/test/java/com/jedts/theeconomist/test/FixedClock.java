package com.jedts.theeconomist.test;

public final class FixedClock {
    private long tick;

    public FixedClock(long initialTick) {
        this.tick = initialTick;
    }

    public long now() {
        return tick;
    }

    public void advance(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        tick += ticks;
    }
}
