package com.jedts.theeconomist.trade.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradePairTest {
    @Test void pairOrderDoesNotDependOnWhoInitiated() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(TradePair.of(first, second), TradePair.of(second, first));
        assertThrows(IllegalArgumentException.class, () -> TradePair.of(first, first));
    }
}
