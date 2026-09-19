package com.jedts.theeconomist.citizen.skin;

import com.jedts.theeconomist.citizen.identity.CitizenModelType;

import java.util.Objects;
import java.util.UUID;

public record ResolvedProfile(UUID profileId, String textureValue, String textureSignature, CitizenModelType modelType) {
    public ResolvedProfile {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(modelType, "modelType");
        if (textureValue == null || textureValue.isBlank()) throw new IllegalArgumentException("textureValue must not be blank");
        if (textureSignature == null || textureSignature.isBlank()) throw new IllegalArgumentException("textureSignature must not be blank");
    }
}
