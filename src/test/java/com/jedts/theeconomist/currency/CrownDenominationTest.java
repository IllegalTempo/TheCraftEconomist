package com.jedts.theeconomist.currency;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CrownDenominationTest {
    @Test
    void exposesTheFixedDenominationTable() {
        assertEquals(
                Map.of("copper_crown", 1, "silver_crown", 10, "gold_crown", 100),
                Arrays.stream(CrownDenomination.values()).collect(Collectors.toMap(
                        CrownDenomination::path,
                        CrownDenomination::unitValue
                ))
        );
    }

    @Test
    void pathsAndValuesAreUnique() {
        assertEquals(3, Arrays.stream(CrownDenomination.values()).map(CrownDenomination::path).distinct().count());
        assertEquals(3, Arrays.stream(CrownDenomination.values()).map(CrownDenomination::unitValue).distinct().count());
    }
}
