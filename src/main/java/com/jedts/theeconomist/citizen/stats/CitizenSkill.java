package com.jedts.theeconomist.citizen.stats;

/**
 * Stable metadata for a citizen skill.
 *
 * @param id stable serialized identifier
 * @param legacyKey old save key accepted while reading existing citizens
 */
public record CitizenSkill(String id, String legacyKey) {
    public CitizenSkill {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Skill IDs must not be blank");
        if (legacyKey == null || legacyKey.isBlank()) {
            throw new IllegalArgumentException("Skill legacy keys must not be blank");
        }
    }

    public static CitizenSkill of(String id, String legacyKey) {
        return new CitizenSkill(id, legacyKey);
    }
}
