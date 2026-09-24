package com.jedts.theeconomist.citizen.identity;

public enum CitizenLifeStage {
    CHILD,
    ADULT;

    public static final long CHILD_DURATION_TICKS = 24_000L;

    public CitizenLifeStage afterTicks(long lifeStageTicks) {
        if (lifeStageTicks < 0) throw new IllegalArgumentException("lifeStageTicks must not be negative");
        return this == CHILD && lifeStageTicks >= CHILD_DURATION_TICKS ? ADULT : this;
    }

    public boolean canWork() {
        return this == ADULT;
    }
}
