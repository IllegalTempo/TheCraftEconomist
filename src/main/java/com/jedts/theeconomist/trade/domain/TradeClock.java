package com.jedts.theeconomist.trade.domain;

@FunctionalInterface
public interface TradeClock {
    long nowTick();
}
