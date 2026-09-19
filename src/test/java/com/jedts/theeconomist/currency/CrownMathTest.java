package com.jedts.theeconomist.currency;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CrownMathTest {
    @Test
    void totalsMixedDenominations() {
        assertEquals(321L, CrownMath.total(Map.of(
                CrownDenomination.COPPER, 1,
                CrownDenomination.SILVER, 2,
                CrownDenomination.GOLD, 3
        )));
    }

    @Test
    void usesLongArithmetic() {
        assertEquals(214_748_364_700L, CrownMath.total(Map.of(
                CrownDenomination.GOLD, Integer.MAX_VALUE
        )));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> CrownMath.total(Map.of(
                CrownDenomination.COPPER, -1
        )));
    }
}
