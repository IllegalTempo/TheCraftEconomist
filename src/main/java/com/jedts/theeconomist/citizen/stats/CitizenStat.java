package com.jedts.theeconomist.citizen.stats;

public record CitizenStat(String id, String legacyKey, int defaultValue) {
    public CitizenStat {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Stat IDs must not be blank");
        if (legacyKey == null || legacyKey.isBlank()) throw new IllegalArgumentException("Stat keys must not be blank");
        if (defaultValue < 0 || defaultValue > CitizenStats.MAX_VALUE) {
            throw new IllegalArgumentException("Stat defaults must be between 0 and 100");
        }
    }

    public static CitizenStat of(String id, String legacyKey, int defaultValue) {
        return new CitizenStat(id, legacyKey, defaultValue);
    }
}
