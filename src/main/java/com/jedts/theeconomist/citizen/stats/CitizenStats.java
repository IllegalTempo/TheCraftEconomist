package com.jedts.theeconomist.citizen.stats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent bounded social, need, and trait values for one Citizen. */
public final class CitizenStats {
    public static final int MAX_VALUE = 100;
    private final Map<CitizenStat, Integer> values;

    private CitizenStats(Map<CitizenStat, Integer> values) {
        Map<CitizenStat, Integer> normalized = new LinkedHashMap<>();
        for (CitizenStat stat : CitizenStatRegistry.all()) {
            normalized.put(stat, clamp(values.getOrDefault(stat, stat.defaultValue())));
        }
        this.values = Collections.unmodifiableMap(normalized);
    }

    public CitizenStats(int hunger, int energy, int safety, int morale, int intelligence, int anger,
                        int education, int ambition, int thrift, int bravery, int sociability, int loyalty) {
        this(values(hunger, energy, safety, morale, intelligence, anger, education, ambition, thrift,
                bravery, sociability, loyalty));
    }

    public static CitizenStats defaults() {
        Map<CitizenStat, Integer> values = new LinkedHashMap<>();
        for (CitizenStat stat : CitizenStatRegistry.all()) values.put(stat, stat.defaultValue());
        return new CitizenStats(values);
    }

    public static CitizenStats fromValues(Map<CitizenStat, Integer> supplied) {
        Map<CitizenStat, Integer> values = new LinkedHashMap<>();
        for (CitizenStat stat : CitizenStatRegistry.all()) {
            values.put(stat, clamp(supplied.getOrDefault(stat, stat.defaultValue())));
        }
        return new CitizenStats(values);
    }

    public int value(CitizenStat stat) {
        return values.getOrDefault(Objects.requireNonNull(stat, "stat"), 0);
    }

    public Map<CitizenStat, Integer> values() {
        return values;
    }

    public int hunger() { return value(CitizenStatRegistry.HUNGER); }
    public int energy() { return value(CitizenStatRegistry.ENERGY); }
    public int safety() { return value(CitizenStatRegistry.SAFETY); }
    public int morale() { return value(CitizenStatRegistry.MORALE); }
    public int intelligence() { return value(CitizenStatRegistry.INTELLIGENCE); }
    public int anger() { return value(CitizenStatRegistry.ANGER); }
    public int education() { return value(CitizenStatRegistry.EDUCATION); }
    public int ambition() { return value(CitizenStatRegistry.AMBITION); }
    public int thrift() { return value(CitizenStatRegistry.THRIFT); }
    public int bravery() { return value(CitizenStatRegistry.BRAVERY); }
    public int sociability() { return value(CitizenStatRegistry.SOCIABILITY); }
    public int loyalty() { return value(CitizenStatRegistry.LOYALTY); }

    public CitizenStats with(CitizenStat stat, int value) {
        if (!CitizenStatRegistry.contains(stat)) throw new IllegalArgumentException("Stat is not registered: " + stat);
        Map<CitizenStat, Integer> updated = new LinkedHashMap<>(values);
        updated.put(stat, clamp(value));
        return new CitizenStats(updated);
    }

    private static Map<CitizenStat, Integer> values(int... raw) {
        Map<CitizenStat, Integer> result = new LinkedHashMap<>();
        int index = 0;
        for (CitizenStat stat : CitizenStatRegistry.all()) result.put(stat, raw[index++]);
        return result;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_VALUE, value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CitizenStats that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
