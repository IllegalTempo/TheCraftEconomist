package com.jedts.theeconomist.citizen.stats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Ordered registry of skills available to citizens.
 *
 * <p>Adding a skill requires adding one definition to {@link #SKILLS}; storage,
 * training, and persistence then discover it automatically.</p>
 */
public final class CitizenSkillRegistry {
    public static final CitizenSkill FARMING = CitizenSkill.of("theeconomist:farming", "Farming");
    public static final CitizenSkill MINING = CitizenSkill.of("theeconomist:mining", "Mining");
    public static final CitizenSkill BUILDING = CitizenSkill.of("theeconomist:building", "Building");
    public static final CitizenSkill COMBAT = CitizenSkill.of("theeconomist:combat", "Combat");
    public static final CitizenSkill TRADE = CitizenSkill.of("theeconomist:trade", "Trade");

    private static final List<CitizenSkill> SKILLS = List.of(FARMING, MINING, BUILDING, COMBAT, TRADE);
    private static final Map<String, CitizenSkill> BY_ID = index(SKILLS);

    private CitizenSkillRegistry() {
    }

    public static List<CitizenSkill> all() {
        return SKILLS;
    }

    public static CitizenSkill byId(String id) {
        return BY_ID.get(id);
    }

    public static boolean contains(CitizenSkill skill) {
        return skill != null && BY_ID.get(skill.id()) == skill;
    }

    private static Map<String, CitizenSkill> index(List<CitizenSkill> skills) {
        Map<String, CitizenSkill> result = new LinkedHashMap<>();
        for (CitizenSkill skill : skills) {
            if (result.put(skill.id(), skill) != null) {
                throw new IllegalArgumentException("Duplicate skill ID: " + skill.id());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
