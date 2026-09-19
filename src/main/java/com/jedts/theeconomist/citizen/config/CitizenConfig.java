package com.jedts.theeconomist.citizen.config;

import java.util.List;

public record CitizenConfig(List<String> profileUsernames, List<String> givenNames, List<String> familyNames) {
    private static final List<String> DEFAULT_GIVEN_NAMES = List.of(
            "Amina", "Diego", "Haruto", "Leila", "Mateo", "Mei",
            "Nia", "Noor", "Priya", "Sofia", "Tariq", "Yuna"
    );
    private static final List<String> DEFAULT_FAMILY_NAMES = List.of(
            "Adebayo", "Dubois", "Garcia", "Haddad", "Ivanov", "Kim",
            "Nakamura", "Okafor", "Patel", "Silva", "Singh", "Wang"
    );

    public CitizenConfig {
        profileUsernames = List.copyOf(profileUsernames);
        givenNames = List.copyOf(givenNames);
        familyNames = List.copyOf(familyNames);
    }

    public static CitizenConfig defaults() {
        return new CitizenConfig(List.of(), DEFAULT_GIVEN_NAMES, DEFAULT_FAMILY_NAMES);
    }
}
