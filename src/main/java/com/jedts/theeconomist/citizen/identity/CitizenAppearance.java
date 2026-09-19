package com.jedts.theeconomist.citizen.identity;

import java.util.Optional;
import java.util.UUID;

public record CitizenAppearance(
        CitizenModelType modelType,
        Optional<UUID> profileId,
        Optional<String> textureValue,
        Optional<String> textureSignature
) {
    public CitizenAppearance {
        if (modelType == null || profileId == null || textureValue == null || textureSignature == null) {
            throw new NullPointerException("appearance fields must not be null");
        }
        textureValue.ifPresent(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("textureValue must not be blank");
        });
        textureSignature.ifPresent(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("textureSignature must not be blank");
        });
    }

    public static CitizenAppearance fallback(UUID citizenId) {
        return new CitizenAppearance(CitizenModelType.forFallback(citizenId), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
