package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorController;
import com.jedts.theeconomist.citizen.behavior.LiveCitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorStopReason;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import com.jedts.theeconomist.citizen.identity.CitizenGender;
import com.jedts.theeconomist.citizen.identity.CitizenFamilyRole;
import com.jedts.theeconomist.citizen.skin.ResolvedProfile;
import com.jedts.theeconomist.citizen.stats.CitizenStats;
import com.jedts.theeconomist.citizen.stats.CitizenStatsSimulator;
import com.jedts.theeconomist.citizen.stats.CitizenStat;
import com.jedts.theeconomist.citizen.stats.CitizenStatRegistry;
import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import com.jedts.theeconomist.citizen.stats.CitizenSkill;
import com.jedts.theeconomist.citizen.stats.CitizenSkillRegistry;
import com.jedts.theeconomist.citizen.job.CitizenJob;
import com.jedts.theeconomist.citizen.job.CitizenOccupation;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.farm.FarmerWorkService;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimHooks;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import com.jedts.theeconomist.citizen.house.HouseholdSavedData;
import com.jedts.theeconomist.citizen.trade.CitizenPriceSnapshot;
import com.jedts.theeconomist.citizen.trade.CitizenPricing;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.info.CitizenOverview;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_CHILD = SynchedEntityData.defineId(
            CitizenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SLIM_MODEL = SynchedEntityData.defineId(
            CitizenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_PROFILE_USERNAME = SynchedEntityData.defineId(
            CitizenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE = SynchedEntityData.defineId(
            CitizenEntity.class, EntityDataSerializers.STRING);
    private CitizenIdentity identity;
    private CitizenStats stats = CitizenStats.defaults();
    private CitizenSkills skills = CitizenSkills.defaults();
    private CitizenJob job = CitizenJob.unemployed();
    private long lifeStageTicks;
    private CitizenFarmInventory farmInventory = new CitizenFarmInventory();
    private final FarmerWorkService farmerWork = new FarmerWorkService(FarmerWorkService.Mode.CROPS);
    private final FarmerWorkService seedFindingWork = new FarmerWorkService(FarmerWorkService.Mode.SEEDS);
    private BlockPos homeSpot;
    private UUID householdId;
    private final CitizenTargetMemory targetMemory = new CitizenTargetMemory();
    private CitizenPriceSnapshot priceSnapshot = new CitizenPriceSnapshot(0, 0, 4, 1, 8);
    private final CitizenBehaviorController behaviorController;
    private boolean profileLookupStarted;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, -1.0f);
        setPathfindingMalus(PathType.LAVA, -1.0f);
        getNavigation().setCanOpenDoors(true);
        behaviorController = CitizenRuntime.behaviorRegistry().createController();
        this.identity = CitizenRuntime.createIdentity(getUUID());
        syncAppearanceData();
        farmInventory.insert(new ItemStack(Items.WOODEN_HOE));
        syncHeldHoe();
        priceSnapshot = new CitizenPriceSnapshot(level.getGameTime(), 0, 4, 1, 8);
        setCustomName(net.minecraft.network.chat.Component.literal(identity.displayName()));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHILD, false);
        builder.define(DATA_SLIM_MODEL, false);
        builder.define(DATA_PROFILE_USERNAME, "");
        builder.define(DATA_SKIN_TEXTURE, "");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDefaultDimensions(pose).scale(isChildCitizen() ? 0.6f : 1.0f);
    }

    public boolean isChildCitizen() { return entityData.get(DATA_CHILD); }
    public boolean usesSlimModel() { return entityData.get(DATA_SLIM_MODEL); }
    public String profileUsernameForRender() { return entityData.get(DATA_PROFILE_USERNAME); }
    public String skinTextureForRender() { return entityData.get(DATA_SKIN_TEXTURE); }

    private void syncAppearanceData() {
        if (identity == null) return;
        entityData.set(DATA_CHILD, identity.lifeStage() == CitizenLifeStage.CHILD);
        entityData.set(DATA_SLIM_MODEL, identity.appearance().modelType() == CitizenModelType.SLIM);
        entityData.set(DATA_PROFILE_USERNAME, identity.profileUsername().orElse(""));
        entityData.set(DATA_SKIN_TEXTURE, identity.appearance().textureValue().orElse(""));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_CHILD.equals(accessor)) refreshDimensions();
    }

    @Override
    protected void registerGoals() {
        // The behavior controller owns citizen actions; this goal handles the
        // physical obstacle that otherwise blocks those actions at wooden doors.
        goalSelector.addGoal(0, new OpenDoorGoal(this, false));
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

    public CitizenFarmInventory farmInventory() {
        return farmInventory;
    }

    public CitizenPriceSnapshot priceSnapshot() {
        return priceSnapshot;
    }

    public String farmStatus() { return farmerWork.status(); }
    public String seedFindingStatus() { return seedFindingWork.status(); }

    public String activeBehaviorId() { return behaviorController.activeId(); }

    public java.util.List<CitizenActionEvaluation> latestBehaviorEvaluations() {
        return behaviorController.latestEvaluations();
    }

    public String activeBehaviorStatus() {
        return level() instanceof ServerLevel serverLevel
                ? behaviorController.activeStatus(new LiveCitizenBehaviorContext(this, serverLevel)) : "";
    }

    public java.util.Optional<com.jedts.theeconomist.citizen.info.CitizenCraftingPreview> activeCraftingPreview() {
        return behaviorController.activeCraftingPreview();
    }

    public Optional<BlockPos> homeSpot() { return Optional.ofNullable(homeSpot); }

    public Optional<UUID> householdId() { return Optional.ofNullable(householdId); }
    public Optional<BlockPos> localTargetLocation(String id) {
        return targetMemory.localTargetLocation(id, level().dimension().identifier().toString());
    }
    public void rememberLocalTarget(String id, BlockPos pos) {
        targetMemory.rememberLocalTarget(id, pos, level().dimension().identifier().toString());
    }
    public void forgetLocalTarget(String id) { targetMemory.forgetLocalTarget(id); }
    public int targetSearchCursor(String id) { return targetMemory.targetSearchCursor(id); }
    public void targetSearchCursor(String id, int value) { targetMemory.targetSearchCursor(id, value); }
    public Optional<String> targetSearchDimension(String id) { return targetMemory.targetSearchDimension(id); }
    public void targetSearchDimension(String id, String dimension) { targetMemory.targetSearchDimension(id, dimension); }

    public String familyMembersSummary() {
        if (identity.familyRole() == CitizenFamilyRole.NONE) return "none";
        if (!(level() instanceof ServerLevel serverLevel) || homeSpot == null) return identity.displayName();
        return HouseholdSavedData.forLevel(serverLevel).ledger().get(homeSpot)
                .map(household -> String.join(", ", household.residentDisplayNames()))
                .filter(names -> !names.isBlank())
                .orElse(identity.displayName());
    }

    public void assignHousehold(UUID houseId, String surname, Set<String> usedGivenNames, BlockPos home) {
        CitizenIdentity generated = CitizenRuntime.createHouseIdentity(getUUID(), surname, usedGivenNames);
        applyHouseholdIdentity(houseId, surname, home, generated);
    }

    /** Assigns a generated-house resident, optionally enforcing a household role gender. */
    public void assignHousehold(UUID houseId, String surname, Set<String> usedGivenNames, BlockPos home,
                                CitizenGender gender) {
        householdId = java.util.Objects.requireNonNull(houseId, "houseId");
        CitizenIdentity generated = CitizenRuntime.createHouseIdentity(getUUID(), surname, usedGivenNames);
        generated = new CitizenIdentity(generated.schemaVersion(), getUUID(), generated.givenName(), surname,
                generated.lifeStage(), generated.profileUsername(), generated.appearance(),
                java.util.Objects.requireNonNull(gender, "gender"));
        applyHouseholdIdentity(houseId, surname, home, generated);
    }

    public void assignHousehold(UUID houseId, String surname, Set<String> usedGivenNames, BlockPos home,
                                CitizenFamilyRole familyRole) {
        CitizenIdentity generated = CitizenRuntime.createHouseIdentity(getUUID(), surname, usedGivenNames);
        CitizenGender gender = familyRole.requiredGender().orElse(generated.gender());
        CitizenLifeStage stage = familyRole == CitizenFamilyRole.CHILD
                ? CitizenLifeStage.CHILD : CitizenLifeStage.ADULT;
        generated = new CitizenIdentity(generated.schemaVersion(), getUUID(), generated.givenName(), surname,
                stage, generated.profileUsername(), generated.appearance(), gender, familyRole);
        lifeStageTicks = 0;
        applyHouseholdIdentity(houseId, surname, home, generated);
    }

    private void applyHouseholdIdentity(UUID houseId, String surname, BlockPos home, CitizenIdentity generated) {
        householdId = java.util.Objects.requireNonNull(houseId, "houseId");
        identity = new CitizenIdentity(generated.schemaVersion(), getUUID(), generated.givenName(), surname,
                generated.lifeStage(), generated.profileUsername(), generated.appearance(), generated.gender(),
                generated.familyRole());
        setCustomName(Component.literal(identity.displayName()));
        setCustomNameVisible(true);
        setHomeSpot(home);
        profileLookupStarted = false;
        syncAppearanceData();
        refreshDimensions();
    }

    public Optional<BlockPos> settleHome(ServerLevel level) {
        if (homeSpot == null) CitizenHomeSite.choose(this, level).ifPresent(this::setHomeSpot);
        return homeSpot();
    }

    private void setHomeSpot(BlockPos spot) {
        homeSpot = spot.immutable();
        setHomeTo(homeSpot, 16);
    }

    public void faceBlock(BlockPos pos) {
        var center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        getLookControl().setLookAt(center.x, center.y, center.z, 360.0f, 360.0f);
        getLookControl().tick();
        setYRot(getYHeadRot());
        setYBodyRot(getYHeadRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            settleHome(serverLevel);
            resolveProfileSkin(serverLevel);
            if (identity.lifeStage() == CitizenLifeStage.CHILD) {
                lifeStageTicks++;
                CitizenLifeStage nextStage = identity.lifeStage().afterTicks(lifeStageTicks);
                if (nextStage != identity.lifeStage()) {
                    identity = identity.withLifeStage(nextStage);
                    lifeStageTicks = 0;
                    syncAppearanceData();
                    refreshDimensions();
                }
            }
            behaviorController.tick(new LiveCitizenBehaviorContext(this, serverLevel));
            setSprinting(!isSleeping() && getNavigation().isInProgress());
            priceSnapshot = CitizenPricing.update(priceSnapshot, level().getGameTime(), farmInventory.sellableWheat());
        }
        if (!level().isClientSide()) syncHeldHoe();
    }

    public void runFarmerWorkCycle() {
        if (identity.lifeStage().canWork()) farmerWork.tick(this);
        syncHeldHoe();
    }

    public void runSeedFindingCycle() {
        if (identity.lifeStage().canWork()) seedFindingWork.tick(this);
        syncHeldHoe();
    }

    public void advanceStatsAndConsiderContracts() {
        stats = CitizenStatsSimulator.advance(stats, false, false, true);
        if (identity.lifeStage().canWork()) considerContracts();
    }

    private void syncHeldHoe() {
        ItemStack visible = ItemStack.EMPTY;
        for (int slot = 0; slot < farmInventory.size(); slot++) {
            if (PlotClaimHooks.isHoe(farmInventory.slot(slot))) {
                visible = farmInventory.slot(slot);
                break;
            }
        }
        if (!ItemStack.matches(getMainHandItem(), visible))
            setItemSlot(EquipmentSlot.MAINHAND, visible.copy());
        // The equipped stack is a visual copy; the inventory owns and drops the real hoe.
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    private void considerContracts() {
        if (job.occupation() != CitizenOccupation.UNEMPLOYED) return;
        CitizenRuntime.contracts().acceptBest(getUUID(), skills.highestValue(), level().getGameTime()).ifPresent(contract -> {
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
        syncAppearanceData();
        return true;
    }

    private void resolveProfileSkin(ServerLevel serverLevel) {
        if (profileLookupStarted || identity.appearance().textureValue().isPresent()) return;
        String username = identity.profileUsername().orElse(null);
        if (username == null || username.isBlank()) return;
        profileLookupStarted = true;
        CitizenRuntime.profileLookup().lookup(username).whenComplete((profile, error) -> {
            if (error != null || profile == null || profile.isEmpty()) return;
            serverLevel.getServer().execute(() -> {
                if (isRemoved() || !isAlive() || level() != serverLevel) return;
                applyResolvedProfile(username, profile.get());
            });
        });
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            com.jedts.theeconomist.citizen.info.CitizenInfoNetworking.send(serverPlayer, this);
        } else if (level().isClientSide()) {
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
        farmInventory.write(output.child("TheEconomistCitizenFarmInventory"));
        ValueOutput priceRoot = output.child("TheEconomistCitizenPrices");
        priceRoot.putLong("CalculatedAtTick", priceSnapshot.calculatedAtTick());
        priceRoot.putLong("Version", priceSnapshot.version());
        priceRoot.putInt("Wheat", priceSnapshot.wheatPrice());
        priceRoot.putInt("Seeds", priceSnapshot.seedPrice());
        priceRoot.putInt("Hoe", priceSnapshot.hoePrice());
        ValueOutput root = output.child("TheEconomistCitizen");
        root.putInt("SchemaVersion", identity.schemaVersion());
        root.putString("CitizenId", identity.citizenId().toString());
        root.putString("GivenName", identity.givenName());
        root.putString("FamilyName", identity.familyName());
        root.putString("LifeStage", identity.lifeStage().name());
        identity.profileUsername().ifPresent(value -> root.putString("ProfileUsername", value));
        root.putString("ModelType", identity.appearance().modelType().name());
        root.putString("Gender", identity.gender().name());
        root.putString("FamilyRole", identity.familyRole().name());
        root.putLong("LifeStageTicks", lifeStageTicks);
        if (householdId != null) root.putString("HouseholdId", householdId.toString());
        if (homeSpot != null) {
            root.putBoolean("HasHomeSpot", true);
            root.putInt("HomeX", homeSpot.getX());
            root.putInt("HomeY", homeSpot.getY());
            root.putInt("HomeZ", homeSpot.getZ());
        }
        targetMemory.write(root);
        identity.appearance().profileId().ifPresent(value -> root.putString("ProfileId", value.toString()));
        identity.appearance().textureValue().ifPresent(value -> root.putString("TextureValue", value));
        identity.appearance().textureSignature().ifPresent(value -> root.putString("TextureSignature", value));
        ValueOutput statRoot = output.child("TheEconomistCitizenStats");
        for (CitizenStat stat : CitizenStatRegistry.all()) {
            statRoot.putInt(stat.id(), stats.value(stat));
        }
        ValueOutput skillRoot = output.child("TheEconomistCitizenSkills");
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            skillRoot.putInt(skill.id(), skills.value(skill));
        }
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
        input.child("TheEconomistCitizenFarmInventory").ifPresent(value -> farmInventory = CitizenFarmInventory.read(value));
        ValueInput priceRoot = input.childOrEmpty("TheEconomistCitizenPrices");
        priceSnapshot = new CitizenPriceSnapshot(
                Math.max(0, priceRoot.getLongOr("CalculatedAtTick", level().getGameTime())),
                Math.max(0, priceRoot.getLongOr("Version", 0)),
                Math.max(1, priceRoot.getIntOr("Wheat", 4)),
                Math.max(1, priceRoot.getIntOr("Seeds", 1)),
                Math.max(1, priceRoot.getIntOr("Hoe", 8)));
        ValueInput root = input.childOrEmpty("TheEconomistCitizen");
        targetMemory.read(root);
        householdId = root.getString("HouseholdId").map(UUID::fromString).orElse(null);
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
        CitizenGender gender;
        try {
            gender = CitizenGender.valueOf(root.getStringOr("Gender", CitizenGender.forFallback(citizenId).name()));
        } catch (IllegalArgumentException ignored) {
            gender = CitizenGender.forFallback(citizenId);
        }
        CitizenFamilyRole familyRole;
        try {
            familyRole = CitizenFamilyRole.valueOf(root.getStringOr("FamilyRole", CitizenFamilyRole.NONE.name()));
        } catch (IllegalArgumentException ignored) {
            familyRole = CitizenFamilyRole.NONE;
        }
        lifeStageTicks = Math.max(0, root.getLongOr("LifeStageTicks", 0));
        Optional<UUID> profileId = root.getString("ProfileId").map(UUID::fromString);
        Optional<String> texture = root.getString("TextureValue");
        Optional<String> signature = root.getString("TextureSignature");
        identity = new CitizenIdentity(schema, citizenId, given, family, stage, profile,
                new CitizenAppearance(model, profileId, texture, signature), gender, familyRole);
        profileLookupStarted = false;
        syncAppearanceData();
        refreshDimensions();
        if (root.getBooleanOr("HasHomeSpot", false))
            setHomeSpot(new BlockPos(root.getIntOr("HomeX", getBlockX()),
                    root.getIntOr("HomeY", getBlockY()), root.getIntOr("HomeZ", getBlockZ())));
        ValueInput statRoot = input.childOrEmpty("TheEconomistCitizenStats");
        var statValues = new java.util.LinkedHashMap<CitizenStat, Integer>();
        for (CitizenStat stat : CitizenStatRegistry.all()) {
            statValues.put(stat, statRoot.getIntOr(stat.id(), statRoot.getIntOr(stat.legacyKey(), stat.defaultValue())));
        }
        stats = CitizenStats.fromValues(statValues);
        ValueInput skillRoot = input.childOrEmpty("TheEconomistCitizenSkills");
        var skillValues = new java.util.LinkedHashMap<CitizenSkill, Integer>();
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            skillValues.put(skill, skillRoot.getIntOr(skill.id(), skillRoot.getIntOr(skill.legacyKey(), 0)));
        }
        skills = CitizenSkills.fromValues(skillValues);
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
        syncHeldHoe();
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel) {
            cleanupBehaviors(serverLevel);
            PlotClaimService.forLevel(serverLevel).releaseCitizen(this);
        }
        super.die(source);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (level() instanceof ServerLevel serverLevel) cleanupBehaviors(serverLevel);
        super.onRemoval(reason);
    }

    private void cleanupBehaviors(ServerLevel level) {
        behaviorController.stopAll(new LiveCitizenBehaviorContext(this, level), CitizenBehaviorStopReason.ENTITY_REMOVED);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int slot = 0; slot < farmInventory.size(); slot++) {
            ItemStack stack = farmInventory.slot(slot);
            if (!stack.isEmpty()) spawnAtLocation(level, stack);
            farmInventory.set(slot, ItemStack.EMPTY);
        }
    }
}
