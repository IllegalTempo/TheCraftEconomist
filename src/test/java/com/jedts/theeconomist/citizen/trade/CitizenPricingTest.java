package com.jedts.theeconomist.citizen.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitizenPricingTest {
    @Test void priceChangesOnlyAtQuarterDayBoundary() {
        CitizenPriceSnapshot initial = new CitizenPriceSnapshot(0, 1, 4, 1, 8);
        assertEquals(initial, CitizenPricing.update(initial, 5_999, 32));
        CitizenPriceSnapshot updated = CitizenPricing.update(initial, 6_000, 32);
        assertEquals(2, updated.wheatPrice());
        assertEquals(2, updated.version());
        assertEquals(updated, CitizenPricing.update(updated, 6_001, 0));
    }

    @Test void wheatScarcityRaisesPriceWithinBounds() {
        CitizenPriceSnapshot initial = new CitizenPriceSnapshot(0, 1, 4, 1, 8);
        assertEquals(6, CitizenPricing.update(initial, 6_000, 0).wheatPrice());
        assertEquals(4, CitizenPricing.update(initial, 6_000, 16).wheatPrice());
        assertEquals(2, CitizenPricing.update(initial, 6_000, 100).wheatPrice());
    }
}
