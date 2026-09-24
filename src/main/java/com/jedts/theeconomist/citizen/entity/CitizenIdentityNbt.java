package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import com.jedts.theeconomist.citizen.identity.CitizenGender;
import com.jedts.theeconomist.citizen.identity.CitizenFamilyRole;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;

public final class CitizenIdentityNbt {
    private static final String ROOT = "TheEconomistCitizen";

    private CitizenIdentityNbt() {
    }

    public static CompoundTag write(CitizenIdentity identity) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", identity.schemaVersion());
        root.putString("CitizenId", identity.citizenId().toString());
        root.putString("GivenName", identity.givenName());
        root.putString("FamilyName", identity.familyName());
        root.putString("LifeStage", identity.lifeStage().name());
        identity.profileUsername().ifPresent(value -> root.putString("ProfileUsername", value));
        root.putString("ModelType", identity.appearance().modelType().name());
        root.putString("Gender", identity.gender().name());
        root.putString("FamilyRole", identity.familyRole().name());
        identity.appearance().profileId().ifPresent(value -> root.putString("ProfileId", value.toString()));
        identity.appearance().textureValue().ifPresent(value -> root.putString("TextureValue", value));
        identity.appearance().textureSignature().ifPresent(value -> root.putString("TextureSignature", value));
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenIdentity read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT)
                .orElseThrow(() -> new IllegalArgumentException("Missing Citizen identity root: " + ROOT));
        int schema = requiredInt(root, "SchemaVersion");
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported Citizen identity schema version: " + schema);
        }
        UUID citizenId = parseUuid(root, "CitizenId");
        String givenName = requiredString(root, "GivenName");
        String familyName = requiredString(root, "FamilyName");
        CitizenLifeStage stage = parseEnum(CitizenLifeStage.class, root, "LifeStage");
        Optional<String> profile = optionalString(root, "ProfileUsername");
        CitizenModelType model = parseEnum(CitizenModelType.class, root, "ModelType");
        CitizenGender gender = root.getString("Gender").map(value -> parseEnum(CitizenGender.class, root, "Gender"))
                .orElseGet(() -> CitizenGender.forFallback(citizenId));
        CitizenFamilyRole familyRole = root.getString("FamilyRole")
                .map(value -> parseEnum(CitizenFamilyRole.class, root, "FamilyRole"))
                .orElse(CitizenFamilyRole.NONE);
        Optional<UUID> profileId = root.contains("ProfileId") ? Optional.of(parseUuid(root, "ProfileId")) : Optional.empty();
        Optional<String> textureValue = optionalString(root, "TextureValue");
        Optional<String> textureSignature = optionalString(root, "TextureSignature");
        return new CitizenIdentity(schema, citizenId, givenName, familyName, stage, profile,
                new CitizenAppearance(model, profileId, textureValue, textureSignature), gender, familyRole);
    }

    private static String requiredString(CompoundTag root, String field) {
        return root.getString(field).filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Missing or blank Citizen identity field: " + field));
    }

    private static Optional<String> optionalString(CompoundTag root, String field) {
        return root.getString(field).filter(value -> !value.isBlank());
    }

    private static int requiredInt(CompoundTag root, String field) {
        return root.getInt(field).orElseThrow(() -> new IllegalArgumentException("Missing Citizen identity field: " + field));
    }

    private static UUID parseUuid(CompoundTag root, String field) {
        try {
            return UUID.fromString(requiredString(root, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Citizen identity field: " + field, exception);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, CompoundTag root, String field) {
        String value = requiredString(root, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Citizen identity field " + field + ": " + value, exception);
        }
    }
}
