package com.jedts.theeconomist.citizen.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CitizenIdentity(
        int schemaVersion,
        UUID citizenId,
        String givenName,
        String familyName,
        CitizenLifeStage lifeStage,
        Optional<String> profileUsername,
        CitizenAppearance appearance
) {
    public CitizenIdentity {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported Citizen identity schema version: " + schemaVersion);
        }
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(lifeStage, "lifeStage");
        Objects.requireNonNull(profileUsername, "profileUsername");
        Objects.requireNonNull(appearance, "appearance");
        if (givenName == null || givenName.isBlank()) {
            throw new IllegalArgumentException("givenName must not be blank");
        }
        if (familyName == null || familyName.isBlank()) {
            throw new IllegalArgumentException("familyName must not be blank");
        }
        profileUsername.ifPresent(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("profileUsername must not be blank");
        });
    }

    public String displayName() {
        return givenName + " " + familyName;
    }
}
