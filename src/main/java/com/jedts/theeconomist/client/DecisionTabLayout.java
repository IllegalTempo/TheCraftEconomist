package com.jedts.theeconomist.client;

/** Pure scroll geometry for the citizen Decision page scrollbar. */
final class DecisionTabLayout {
    private static final int MIN_THUMB_HEIGHT = 16;

    private DecisionTabLayout() { }

    static int maxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    static int thumbHeight(int trackHeight, int contentHeight, int viewportHeight) {
        if (trackHeight <= 0) return 0;
        if (contentHeight <= viewportHeight || contentHeight <= 0) return trackHeight;
        return Math.min(trackHeight, Math.max(MIN_THUMB_HEIGHT,
                (int)Math.round(trackHeight * (double)viewportHeight / contentHeight)));
    }

    static int thumbTop(int trackTop, int scroll, int maxScroll, int thumbHeight, int trackHeight) {
        int travel = Math.max(0, trackHeight - thumbHeight);
        if (maxScroll <= 0 || travel == 0) return trackTop;
        double progress = Math.max(0.0, Math.min(1.0, scroll / (double)maxScroll));
        return trackTop + (int)Math.round(travel * progress);
    }

    static int scrollFromThumb(int mouseY, int trackTop, int dragOffset, int trackHeight,
                               int contentHeight, int viewportHeight) {
        int maxScroll = maxScroll(contentHeight, viewportHeight);
        if (maxScroll == 0) return 0;
        int thumbHeight = thumbHeight(trackHeight, contentHeight, viewportHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        if (travel == 0) return 0;
        int top = Math.max(trackTop, Math.min(trackTop + travel, mouseY - dragOffset));
        return (int)Math.round((top - trackTop) / (double)travel * maxScroll);
    }
}
