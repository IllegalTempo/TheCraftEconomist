package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import com.jedts.theeconomist.citizen.skin.ResolvedProfile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Optional;
import java.util.UUID;

public final class CitizenEntity extends PathfinderMob {
    private CitizenIdentity identity;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        this.identity = CitizenRuntime.createIdentity(getUUID());
        setCustomName(net.minecraft.network.chat.Component.literal(identity.displayName()));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 1.25, 1.5));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public CitizenIdentity identity() {
        return identity;
    }

    public boolean applyResolvedProfile(String assignedUsername, ResolvedProfile profile) {
        Optional<CitizenIdentity> updated = identity.withResolvedProfile(assignedUsername, profile);
        if (updated.isEmpty()) return false;
        identity = updated.get();
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            try {
                Class<?> hooks = Class.forName("com.jedts.theeconomist.client.CitizenClientHooks");
                hooks.getMethod("open", Object.class).invoke(null, this);
            } catch (ReflectiveOperationException ignored) {
                // Dedicated-server-safe: client UI classes are optional at runtime.
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        ValueOutput root = output.child("TheEconomistCitizen");
        root.putInt("SchemaVersion", identity.schemaVersion());
        root.putString("CitizenId", identity.citizenId().toString());
        root.putString("GivenName", identity.givenName());
        root.putString("FamilyName", identity.familyName());
        root.putString("LifeStage", identity.lifeStage().name());
        identity.profileUsername().ifPresent(value -> root.putString("ProfileUsername", value));
        root.putString("ModelType", identity.appearance().modelType().name());
        identity.appearance().profileId().ifPresent(value -> root.putString("ProfileId", value.toString()));
        identity.appearance().textureValue().ifPresent(value -> root.putString("TextureValue", value));
        identity.appearance().textureSignature().ifPresent(value -> root.putString("TextureSignature", value));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ValueInput root = input.childOrEmpty("TheEconomistCitizen");
        int schema = root.getIntOr("SchemaVersion", 1);
        UUID citizenId = UUID.fromString(root.getStringOr("CitizenId", getUUID().toString()));
        CitizenIdentity generated = CitizenRuntime.createIdentity(citizenId);
        String given = root.getStringOr("GivenName", generated.givenName());
        String family = root.getStringOr("FamilyName", generated.familyName());
        if (given.equals("Citizen") && family.equals("Unknown")) {
            given = generated.givenName();
            family = generated.familyName();
        }
        CitizenLifeStage stage = CitizenLifeStage.valueOf(root.getStringOr("LifeStage", CitizenLifeStage.ADULT.name()));
        Optional<String> profile = root.getString("ProfileUsername").or(() -> generated.profileUsername());
        CitizenModelType model = CitizenModelType.valueOf(root.getStringOr("ModelType", CitizenModelType.forFallback(citizenId).name()));
        Optional<UUID> profileId = root.getString("ProfileId").map(UUID::fromString);
        Optional<String> texture = root.getString("TextureValue");
        Optional<String> signature = root.getString("TextureSignature");
        identity = new CitizenIdentity(schema, citizenId, given, family, stage, profile,
                new CitizenAppearance(model, profileId, texture, signature));
        setCustomName(net.minecraft.network.chat.Component.literal(identity.displayName()));
        setCustomNameVisible(true);
    }
}
