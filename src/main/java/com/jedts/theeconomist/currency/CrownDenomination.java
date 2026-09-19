package com.jedts.theeconomist.currency;

public enum CrownDenomination {
    COPPER("copper_crown", 1),
    SILVER("silver_crown", 10),
    GOLD("gold_crown", 100);

    private final String path;
    private final int unitValue;

    CrownDenomination(String path, int unitValue) {
        this.path = path;
        this.unitValue = unitValue;
    }

    public String path() {
        return path;
    }

    public int unitValue() {
        return unitValue;
    }
}
