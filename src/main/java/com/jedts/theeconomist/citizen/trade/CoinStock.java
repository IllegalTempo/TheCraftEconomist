package com.jedts.theeconomist.citizen.trade;

public record CoinStock(int copper, int silver, int gold) {
    public CoinStock {
        if (copper < 0 || silver < 0 || gold < 0)
            throw new IllegalArgumentException("coin counts must be nonnegative");
    }

    public long value() {
        return (long) copper + 10L * silver + 100L * gold;
    }

    public int coins() { return copper + silver + gold; }

    public boolean contains(CoinStock other) {
        return copper >= other.copper && silver >= other.silver && gold >= other.gold;
    }
}
