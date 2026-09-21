package com.jedts.theeconomist.citizen.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

public final class CitizenSkills {
    public static final int MAX_VALUE = 100;

    private final Map<CitizenSkill, Integer> values;

    private CitizenSkills(Map<CitizenSkill, Integer> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static CitizenSkills defaults() {
        Map<CitizenSkill, Integer> values = new LinkedHashMap<>();
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            values.put(skill, 0);
        }
        return new CitizenSkills(values);
    }

    public static CitizenSkills fromValues(Map<CitizenSkill, Integer> values) {
        Map<CitizenSkill, Integer> normalized = new LinkedHashMap<>();
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            normalized.put(skill, clamp(values.getOrDefault(skill, 0)));
        }
        return new CitizenSkills(normalized);
    }

    public int value(CitizenSkill skill) {
        return values.getOrDefault(Objects.requireNonNull(skill, "skill"), 0);
    }

    public Map<CitizenSkill, Integer> values() {
        return values;
    }

    public CitizenSkills train(CitizenSkill skill, int amount) {
        Objects.requireNonNull(skill, "skill");
        if (!CitizenSkillRegistry.contains(skill)) {
            throw new IllegalArgumentException("Skill is not registered: " + skill.id());
        }
        if (amount < 0) throw new IllegalArgumentException("training amount must not be negative");
        Map<CitizenSkill, Integer> updated = new LinkedHashMap<>(values);
        updated.put(skill, clamp(value(skill) + amount));
        return new CitizenSkills(updated);
    }

    public int highestValue() {
        return values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    // Compatibility accessors for callers migrating from the fixed skill record.
    public int farming() { return value(CitizenSkillRegistry.FARMING); }
    public int mining() { return value(CitizenSkillRegistry.MINING); }
    public int building() { return value(CitizenSkillRegistry.BUILDING); }
    public int combat() { return value(CitizenSkillRegistry.COMBAT); }
    public int trade() { return value(CitizenSkillRegistry.TRADE); }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_VALUE, value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CitizenSkills that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
