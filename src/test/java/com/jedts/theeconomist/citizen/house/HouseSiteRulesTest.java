package com.jedts.theeconomist.citizen.house;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HouseSiteRulesTest {
    @Test void acceptsDryLevelAndGentleSlopes() {
        assertTrue(HouseSiteRules.accepts(new HouseSiteRules.Site(true, 0, true)));
        assertTrue(HouseSiteRules.accepts(new HouseSiteRules.Site(true, 1, true)));
        assertTrue(HouseSiteRules.accepts(new HouseSiteRules.Site(true, 2, true)));
    }
    @Test void rejectsWaterCliffsBlockedEntrancesAndMissingSamples() {
        assertFalse(HouseSiteRules.accepts(new HouseSiteRules.Site(false, 0, true)));
        assertFalse(HouseSiteRules.accepts(new HouseSiteRules.Site(true, 3, true)));
        assertFalse(HouseSiteRules.accepts(new HouseSiteRules.Site(true, 0, false)));
        assertFalse(HouseSiteRules.accepts(null));
        assertFalse(HouseSiteRules.accepts(new HouseSiteRules.Site(true, -1, true)));
    }
}
