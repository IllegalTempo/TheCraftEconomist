package com.jedts.theeconomist.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DecisionTabLayoutTest {
    @Test
    void computesScrollRangeAndProportionalThumb() {
        assertEquals(0, DecisionTabLayout.maxScroll(180, 240));
        assertEquals(360, DecisionTabLayout.maxScroll(600, 240));
        assertEquals(80, DecisionTabLayout.thumbHeight(240, 600, 200));
        assertEquals(88, DecisionTabLayout.thumbTop(40, 120, 400, 80, 240));
    }

    @Test
    void mapsThumbDragToClampedContentScroll() {
        assertEquals(0, DecisionTabLayout.scrollFromThumb(80, 40, 40, 240, 600, 100));
        assertEquals(500, DecisionTabLayout.scrollFromThumb(280, 40, 40, 240, 600, 100));
        assertEquals(200, DecisionTabLayout.scrollFromThumb(160, 40, 40, 240, 600, 100));
    }
}
