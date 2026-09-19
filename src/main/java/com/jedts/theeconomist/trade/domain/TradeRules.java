package com.jedts.theeconomist.trade.domain;

public final class TradeRules {
    public static final long REQUEST_TIMEOUT_TICKS = 300L;
    public static final double MAX_DISTANCE_SQUARED = 256.0;
    public static final int COUNTDOWN_TICKS = 60;
    public static final int OFFER_SLOTS = 18;

    private TradeRules() {
    }
}
