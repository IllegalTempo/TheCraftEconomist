package com.jedts.theeconomist.trade.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class TradeRulesTest {
    @Test void approvedRulesArePinned() {
        assertEquals(300L, TradeRules.REQUEST_TIMEOUT_TICKS);
        assertEquals(256.0, TradeRules.MAX_DISTANCE_SQUARED);
        assertEquals(60, TradeRules.COUNTDOWN_TICKS);
        assertEquals(18, TradeRules.OFFER_SLOTS);
    }
}
