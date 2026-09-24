package com.jedts.theeconomist.citizen.behavior.sleep;

public final class CitizenSleepSchedule {
    public static final long START_TICK = 12_542L;
    public static final long END_TICK = 23_459L;
    private static final long DAY_TICKS = 24_000L;

    private CitizenSleepSchedule() { }

    public static boolean isSleepingTime(long dayTime) {
        long tick = Math.floorMod(dayTime, DAY_TICKS);
        return tick >= START_TICK && tick <= END_TICK;
    }
}
