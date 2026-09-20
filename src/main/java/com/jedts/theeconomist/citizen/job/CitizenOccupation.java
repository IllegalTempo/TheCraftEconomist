package com.jedts.theeconomist.citizen.job;

public enum CitizenOccupation {
    UNEMPLOYED, FARMER, MINER, BUILDER, SOLDIER, TRADER;

    public static CitizenOccupation fromContractTarget(String target) {
        String normalized = target == null ? "" : target.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("farm") || normalized.contains("crop")) return FARMER;
        if (normalized.contains("mine") || normalized.contains("ore") || normalized.contains("stone")) return MINER;
        if (normalized.contains("build") || normalized.contains("house") || normalized.contains("construct")) return BUILDER;
        if (normalized.contains("guard") || normalized.contains("defend") || normalized.contains("army")) return SOLDIER;
        if (normalized.contains("trade") || normalized.contains("deliver") || normalized.contains("transport")) return TRADER;
        return UNEMPLOYED;
    }
}
