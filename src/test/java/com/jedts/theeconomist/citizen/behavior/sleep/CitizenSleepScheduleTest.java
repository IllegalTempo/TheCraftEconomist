package com.jedts.theeconomist.citizen.behavior.sleep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenSleepScheduleTest {
    @Test
    void sleepingWindowUsesWrappedDayTimeBoundaries() {
        assertFalse(CitizenSleepSchedule.isSleepingTime(12_541));
        assertTrue(CitizenSleepSchedule.isSleepingTime(12_542));
        assertTrue(CitizenSleepSchedule.isSleepingTime(23_459));
        assertFalse(CitizenSleepSchedule.isSleepingTime(23_460));
        assertTrue(CitizenSleepSchedule.isSleepingTime(36_542));
    }
}
