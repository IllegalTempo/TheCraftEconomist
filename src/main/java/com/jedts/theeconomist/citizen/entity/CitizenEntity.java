package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import com.jedts.theeconomist.citizen.skin.ResolvedProfile;
import com.jedts.theeconomist.citizen.stats.CitizenStats;
import com.jedts.theeconomist.citizen.stats.CitizenStatsSimulator;
import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import com.jedts.theeconomist.citizen.job.CitizenJob;
import com.jedts.theeconomist.citizen.job.CitizenOccupation;
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
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

public final class CitizenEntity extends PathfinderMob {
    private CitizenIdentity identity;
    private CitizenStats stats = CitizenStats.defaults();
    private CitizenSkills skills = CitizenSkills.defaults();
    private CitizenJob job = CitizenJob.unemployed();
    private int statsTickCounter;

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

    public CitizenStats stats() {
        return stats;
    }

    public CitizenSkills skills() {
        return skills;
    }

    public CitizenJob job() {
        return job;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && ++statsTickCounter >= 200) {
            statsTickCounter = 0;
            stats = CitizenStatsSimulator.advance(stats, false, false, true);
            considerContracts();
        }
    }

    private void considerContracts() {
        if (job.occupation() != CitizenOccupation.UNEMPLOYED) return;
        int skill = Math.max(Math.max(skills.farming(), skills.mining()), Math.max(skills.building(),
                Math.max(skills.combat(), skills.trade())));
        CitizenRuntime.contracts().acceptBest(getUUID(), skill, level().getGameTime()).ifPresent(contract -> {
            notifyRequester(contract);
            CitizenOccupation occupation = CitizenOccupation.fromContractTarget(contract.target());
            if (occupation != CitizenOccupation.UNEMPLOYED) {
                job = new CitizenJob(occupation, "Contract " + contract.id(), contract.bounty(), 8, 16);
                contract.start(getUUID());
            }
        });
    }

    private void notifyRequester(com.jedts.theeconomist.contract.CitizenContract contract) {
        Player requester = level().getPlayerInAnyDimension(contract.requesterId());
        if (requester == null) return;
        String citizenName = getCustomName() == null ? getName().getString() : getCustomName().getString();
        requester.sendSystemMessage(Component.literal(citizenName + " accepted your "
                + contract.kind().name().toLowerCase(java.util.Locale.ROOT) + " contract for "
                + contract.target() + " (" + contract.bounty() + " Crowns)."));
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
        ValueOutput statRoot = output.child("TheEconomistCitizenStats");
        statRoot.putInt("Hunger", stats.hunger());
        statRoot.putInt("Energy", stats.energy());
        statRoot.putInt("Safety", stats.safety());
        statRoot.putInt("Morale", stats.morale());
        statRoot.putInt("Intelligence", stats.intelligence());
        statRoot.putInt("Anger", stats.anger());
        statRoot.putInt("Education", stats.education());
        statRoot.putInt("Ambition", stats.ambition());
        statRoot.putInt("Thrift", stats.thrift());
        statRoot.putInt("Bravery", stats.bravery());
        statRoot.putInt("Sociability", stats.sociability());
        statRoot.putInt("Loyalty", stats.loyalty());
        ValueOutput skillRoot = output.child("TheEconomistCitizenSkills");
        skillRoot.putInt("Farming", skills.farming());
        skillRoot.putInt("Mining", skills.mining());
        skillRoot.putInt("Building", skills.building());
        skillRoot.putInt("Combat", skills.combat());
        skillRoot.putInt("Trade", skills.trade());
        ValueOutput jobRoot = output.child("TheEconomistCitizenJob");
        jobRoot.putString("Occupation", job.occupation().name());
        jobRoot.putString("Employer", job.employer());
        jobRoot.putInt("WagePerDay", job.wagePerDay());
        jobRoot.putInt("StartHour", job.startHour());
        jobRoot.putInt("EndHour", job.endHour());
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
        ValueInput statRoot = input.childOrEmpty("TheEconomistCitizenStats");
        stats = new CitizenStats(
                statRoot.getIntOr("Hunger", 100), statRoot.getIntOr("Energy", 100),
                statRoot.getIntOr("Safety", 50), statRoot.getIntOr("Morale", 50),
                statRoot.getIntOr("Intelligence", 50), statRoot.getIntOr("Anger", 0),
                statRoot.getIntOr("Education", 0), statRoot.getIntOr("Ambition", 50),
                statRoot.getIntOr("Thrift", 50), statRoot.getIntOr("Bravery", 50),
                statRoot.getIntOr("Sociability", 50), statRoot.getIntOr("Loyalty", 50));
        ValueInput skillRoot = input.childOrEmpty("TheEconomistCitizenSkills");
        skills = new CitizenSkills(skillRoot.getIntOr("Farming", 0), skillRoot.getIntOr("Mining", 0),
                skillRoot.getIntOr("Building", 0), skillRoot.getIntOr("Combat", 0), skillRoot.getIntOr("Trade", 0));
        ValueInput jobRoot = input.childOrEmpty("TheEconomistCitizenJob");
        try {
            job = new CitizenJob(com.jedts.theeconomist.citizen.job.CitizenOccupation.valueOf(
                    jobRoot.getStringOr("Occupation", "UNEMPLOYED")), jobRoot.getStringOr("Employer", ""),
                    jobRoot.getIntOr("WagePerDay", 0), jobRoot.getIntOr("StartHour", 0), jobRoot.getIntOr("EndHour", 0));
        } catch (IllegalArgumentException ignored) {
            job = CitizenJob.unemployed();
        }
        setCustomName(net.minecraft.network.chat.Component.literal(identity.displayName()));
        setCustomNameVisible(true);
    }
}
