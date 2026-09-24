package com.jedts.theeconomist.citizen.identity;

import com.jedts.theeconomist.citizen.skin.ResolvedProfile;

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
        CitizenAppearance appearance,
        CitizenGender gender,
        CitizenFamilyRole familyRole
) {
    public CitizenIdentity(int schemaVersion, UUID citizenId, String givenName, String familyName,
                           CitizenLifeStage lifeStage, Optional<String> profileUsername,
                           CitizenAppearance appearance) {
        this(schemaVersion, citizenId, givenName, familyName, lifeStage, profileUsername, appearance,
                CitizenGender.forFallback(citizenId), CitizenFamilyRole.NONE);
    }

    public CitizenIdentity(int schemaVersion, UUID citizenId, String givenName, String familyName,
                           CitizenLifeStage lifeStage, Optional<String> profileUsername,
                           CitizenAppearance appearance, CitizenGender gender) {
        this(schemaVersion, citizenId, givenName, familyName, lifeStage, profileUsername, appearance,
                gender, CitizenFamilyRole.NONE);
    }

    public CitizenIdentity {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported Citizen identity schema version: " + schemaVersion);
        }
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(lifeStage, "lifeStage");
        Objects.requireNonNull(profileUsername, "profileUsername");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(familyRole, "familyRole");
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

    public Optional<CitizenIdentity> withResolvedProfile(String assignedUsername, ResolvedProfile profile) {
        if (assignedUsername == null || profile == null || profileUsername.isEmpty()
                || !profileUsername.get().equals(assignedUsername)) {
            return Optional.empty();
        }
        return Optional.of(new CitizenIdentity(schemaVersion, citizenId, givenName, familyName, lifeStage,
                profileUsername, new CitizenAppearance(profile.modelType(), Optional.of(profile.profileId()),
                        Optional.of(profile.textureValue()), Optional.of(profile.textureSignature())), gender, familyRole));
    }

    public CitizenIdentity withLifeStage(CitizenLifeStage newLifeStage) {
        return new CitizenIdentity(schemaVersion, citizenId, givenName, familyName, newLifeStage,
                profileUsername, appearance, gender, familyRole);
    }
}
