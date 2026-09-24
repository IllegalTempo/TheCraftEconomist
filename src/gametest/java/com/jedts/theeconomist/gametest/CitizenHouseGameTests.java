package com.jedts.theeconomist.gametest;

import com.jedts.theeconomist.citizen.house.CitizenHousePiece;
import com.jedts.theeconomist.citizen.house.CitizenHouseStructures;
import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class CitizenHouseGameTests {
    @GameTest
    public void citizenUsesNoMinecraftGoals(GameTestHelper helper) {
        var citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        try {
            var goalField = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
            var targetField = net.minecraft.world.entity.Mob.class.getDeclaredField("targetSelector");
            goalField.setAccessible(true);
            targetField.setAccessible(true);
            var goals = (net.minecraft.world.entity.ai.goal.GoalSelector) goalField.get(citizen);
            var targets = (net.minecraft.world.entity.ai.goal.GoalSelector) targetField.get(citizen);
            if (!goals.getAvailableGoals().isEmpty() || !targets.getAvailableGoals().isEmpty())
                throw new AssertionError("Citizen registered Minecraft goals");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not inspect Citizen goal selectors", exception);
        }
        helper.succeed();
    }

    @GameTest(padding = 32, maxTicks = 300)
    public void householdResidentsUseDifferentBedsAndWakeAtDay(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getStructureTemplateManager();
        var template = manager.get(CitizenHouseStructures.HOUSE_ID).orElseThrow();
        var piece = new CitizenHousePiece(manager, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE);
        if (!template.placeInWorld(level, piece.templatePosition(), piece.templatePosition(), piece.placeSettings(),
                level.getRandom(), 2)) throw new AssertionError("house template failed to place");
        BlockPos anchor = piece.entrance();
        forceHouseChunks(level, anchor);
        var observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        setDayTime(level, 13_000);
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        var household = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level)
                .ledger().get(anchor).orElseThrow();
        var residents = new java.util.ArrayList<CitizenEntity>();
        for (int slot = 0; slot < household.residentCount(); slot++) {
            residents.add((CitizenEntity) level.getEntity(household.slotId(slot)));
        }
        helper.setBlock(new BlockPos(12, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(12, 2, 4), Blocks.STONE);
        var homeless = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(12, 3, 2));
        var missingRecord = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(12, 3, 4));
        missingRecord.assignHousehold(java.util.UUID.randomUUID(), "Missing", java.util.Set.of(),
                helper.absolutePos(new BlockPos(12, 3, 6)),
                com.jedts.theeconomist.citizen.identity.CitizenFamilyRole.DAD);
        helper.runAfterDelay(180, () -> {
            long sleepers = residents.stream().filter(CitizenEntity::isSleeping).count();
            if (sleepers == 0) throw new AssertionError("no resident reached a bed; clock="
                    + level.getDefaultClockTime() + "; residents=" + residents.stream()
                    .map(citizen -> citizen.activeBehaviorId() + "/" + citizen.activeBehaviorStatus()
                            + "@" + citizen.blockPosition() + ",ticks=" + citizen.tickCount
                            + ",home=" + citizen.homeSpot() + ",household=" + citizen.householdId()
                            + ",record=" + citizen.homeSpot().flatMap(pos -> com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level)
                                    .ledger().get(pos)).isPresent()).toList() + "; beds="
                    + java.util.stream.StreamSupport.stream(BlockPos.betweenClosed(
                                    anchor.offset(-30, -8, -30), anchor.offset(30, 8, 30)).spliterator(), false)
                            .filter(pos -> level.getBlockState(pos).getBlock()
                                    instanceof net.minecraft.world.level.block.AbstractBedBlock)
                            .map(pos -> pos.subtract(anchor)).toList());
            long distinctBeds = residents.stream().filter(CitizenEntity::isSleeping)
                    .map(citizen -> citizen.getSleepingPos().orElseThrow()).distinct().count();
            if (distinctBeds != sleepers) throw new AssertionError("residents shared a bed");
            if (!homeless.activeBehaviorId().equals("confused")
                    || !homeless.activeBehaviorStatus().equals("No generated household"))
                throw new AssertionError("homeless Citizen did not enter the expected confused state");
            if (!missingRecord.activeBehaviorId().equals("confused")
                    || !missingRecord.activeBehaviorStatus().equals("Household record missing"))
                throw new AssertionError("missing household record did not produce the expected confused state");
            setDayTime(level, 1_000);
            helper.runAfterDelay(20, () -> {
                if (residents.stream().anyMatch(CitizenEntity::isSleeping))
                    throw new AssertionError("resident remained asleep during day");
                if (homeless.activeBehaviorId().equals("confused")
                        || missingRecord.activeBehaviorId().equals("confused"))
                    throw new AssertionError("confusion continued during day; clock=" + level.getDefaultClockTime()
                            + "; homeless=" + homeless.activeBehaviorId() + "/" + homeless.activeBehaviorStatus()
                            + ",ticks=" + homeless.tickCount + "; missing=" + missingRecord.activeBehaviorId()
                            + "/" + missingRecord.activeBehaviorStatus() + ",ticks=" + missingRecord.tickCount);
                helper.succeed();
            });
        });
    }

    private static void setDayTime(net.minecraft.server.level.ServerLevel level, long ticks) {
        var overworldClock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        level.clockManager().setTotalTicks(overworldClock, ticks);
    }

    private static void forceHouseChunks(net.minecraft.server.level.ServerLevel level, BlockPos anchor) {
        for (int x = (anchor.getX() - 12) >> 4; x <= (anchor.getX() + 12) >> 4; x++)
            for (int z = (anchor.getZ() - 12) >> 4; z <= (anchor.getZ() + 12) >> 4; z++)
                level.setChunkForced(x, z, true);
    }

    @GameTest
    public void populationSkipsUnloadedAnchor(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = new BlockPos(29000000, 70, 29000000);
        if (level.hasChunkAt(anchor)) throw new AssertionError("fixture anchor already loaded");
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        if (level.hasChunkAt(anchor)
                || com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(anchor).isPresent())
            throw new AssertionError("population loaded an anchor or created its ledger entry");
        helper.succeed();
    }

    @GameTest(padding = 32)
    public void householdPopulationIssuesOnceAndNeverReplacesDeath(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 3, 8));
        preparePopulationGround(helper, anchor, false);
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        var household = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(anchor).orElseThrow();
        if (household.residentCount() < 2 || household.residentCount() > 4 || !household.unissuedSlots().isEmpty())
            throw new AssertionError("initial slots not populated");
        var names = new java.util.HashSet<String>();
        for (int slot = 0; slot < household.residentCount(); slot++) {
            var citizen = (CitizenEntity) level.getEntity(household.slotId(slot));
            if (citizen == null || !citizen.identity().familyName().equals(household.surname())
                    || !citizen.homeSpot().orElseThrow().equals(anchor)
                    || citizen.distanceToSqr(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5) > 64)
                throw new AssertionError("invalid resident identity, home or position");
            if (slot == 0 && citizen.identity().gender() != com.jedts.theeconomist.citizen.identity.CitizenGender.FEMALE)
                throw new AssertionError("first resident is not mom");
            if (slot == 1 && citizen.identity().gender() != com.jedts.theeconomist.citizen.identity.CitizenGender.MALE)
                throw new AssertionError("second resident is not dad");
            var expectedRole = com.jedts.theeconomist.citizen.identity.CitizenFamilyRole.forHouseholdSlot(slot);
            if (citizen.identity().familyRole() != expectedRole)
                throw new AssertionError("resident has wrong family role");
            var expectedStage = slot < 2
                    ? com.jedts.theeconomist.citizen.identity.CitizenLifeStage.ADULT
                    : com.jedts.theeconomist.citizen.identity.CitizenLifeStage.CHILD;
            if (citizen.identity().lifeStage() != expectedStage)
                throw new AssertionError("resident has wrong life stage");
            names.add(citizen.identity().givenName());
        }
        if (names.size() != household.residentCount()) throw new AssertionError("given names repeat");
        var original = (CitizenEntity) level.getEntity(household.slotId(1));
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        original.saveWithoutId(output);
        var restored = new CitizenEntity(CitizenEntities.CITIZEN, level);
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess(), output.buildResult()));
        if (!restored.identity().equals(original.identity()) || !restored.householdId().equals(original.householdId())
                || !restored.homeSpot().equals(original.homeSpot()) || !restored.getUUID().equals(original.getUUID()))
            throw new AssertionError("resident changed on NBT reload");
        var codec = com.jedts.theeconomist.citizen.house.HouseholdSavedData.TYPE.codec();
        var encoded = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level)).getOrThrow();
        var loadedData = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        level.getDataStorage().set(com.jedts.theeconomist.citizen.house.HouseholdSavedData.TYPE, loadedData);
        level.getEntity(household.slotId(0)).discard();
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        if (level.getEntity(household.slotId(0)) != null || !household.unissuedSlots().isEmpty())
            throw new AssertionError("dead resident replaced");
        helper.succeed();
    }

    @GameTest(padding = 32)
    public void registeredStructureStartQueuesPopulationOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 3, 8));
        preparePopulationGround(helper, anchor, false);
        var chunk = level.getChunkAt(anchor);
        com.jedts.theeconomist.citizen.house.HousePopulationService.queueHouseStarts(level, chunk);
        if (com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(anchor).isPresent())
            throw new AssertionError("ordinary blocks made a household");
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(CitizenHouseStructures.HOUSE_KEY).value();
        var piece = new CitizenHousePiece(level.getStructureTemplateManager(), anchor.offset(-4, -1, 0), Rotation.NONE);
        var context = StructurePieceSerializationContext.fromLevel(level);
        var savedPiece = new CitizenHousePiece(context, piece.createTag(context));
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunk.getPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(savedPiece)));
        chunk.setStartForStructure(structure, start);
        com.jedts.theeconomist.citizen.house.HousePopulationService.queueHouseStarts(level, chunk);
        com.jedts.theeconomist.citizen.house.HousePopulationService.queueHouseStarts(level, chunk);
        helper.runAfterDelay(2, () -> {
            var household = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(anchor).orElseThrow();
            if (!household.unissuedSlots().isEmpty()) throw new AssertionError("queued start not populated");
            chunk.setStartForStructure(structure, net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START);
            helper.succeed();
        });
    }

    @GameTest(padding = 32)
    public void blockedHouseRetriesUnissuedSlotsAndKeepsSeparateHouseholds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 3, 8));
        preparePopulationGround(helper, anchor, true);
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        var household = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(anchor).orElseThrow();
        if (household.unissuedSlots().size() != household.residentCount()) throw new AssertionError("blocked slots issued");
        preparePopulationGround(helper, anchor, false);
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
        if (!household.unissuedSlots().isEmpty()) throw new AssertionError("slots did not retry");
        BlockPos second = anchor.offset(20, 0, 0);
        preparePopulationGround(helper, second, false);
        com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, second);
        var other = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level).ledger().get(second).orElseThrow();
        for (int slot = 0; slot < other.residentCount(); slot++) {
            var citizen = (CitizenEntity) level.getEntity(other.slotId(slot));
            if (citizen == null || !citizen.identity().familyName().equals(other.surname())
                    || !citizen.homeSpot().orElseThrow().equals(second)) throw new AssertionError("second household mismatch");
        }
        helper.succeed();
    }

    @GameTest(padding = 32)
    public void partialPopulationKeepsUnloadedResidentsNamesAfterReload(GameTestHelper helper) throws Exception {
        // Always choose the first available name so a lost reservation reliably repeats it.
        var field = com.jedts.theeconomist.citizen.CitizenRuntime.class.getDeclaredField("identityFactory");
        field.setAccessible(true);
        com.jedts.theeconomist.citizen.CitizenRuntime.initialize();
        var originalFactory = field.get(null);
        field.set(null, new com.jedts.theeconomist.citizen.identity.CitizenIdentityFactory(new java.util.Random() {
            @Override public int nextInt(int bound) { return 0; }
        }));
        try {
            var level = helper.getLevel();
            BlockPos anchor = helper.absolutePos(new BlockPos(8, 3, 8));
            preparePopulationGround(helper, anchor, true);
            level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(anchor.above(), Blocks.AIR.defaultBlockState());
            com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
            var type = com.jedts.theeconomist.citizen.house.HouseholdSavedData.TYPE;
            var data = com.jedts.theeconomist.citizen.house.HouseholdSavedData.forLevel(level);
            var household = data.ledger().get(anchor).orElseThrow();
            if (household.unissuedSlots().size() != household.residentCount() - 1)
                throw new AssertionError("fixture must issue exactly one resident");
            var first = (CitizenEntity) level.getEntity(household.slotId(0));
            String firstName = first.identity().givenName();
            first.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            if (level.getEntity(household.slotId(0)) != null) throw new AssertionError("resident still loaded");
            var encoded = type.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
            level.getDataStorage().set(type, type.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow());
            preparePopulationGround(helper, anchor, false);
            com.jedts.theeconomist.citizen.house.HousePopulationService.populateLoadedHouse(level, anchor);
            var names = new java.util.HashSet<String>();
            names.add(firstName);
            for (int slot = 1; slot < household.residentCount(); slot++) {
                var resident = (CitizenEntity) level.getEntity(household.slotId(slot));
                if (resident == null || !names.add(resident.identity().givenName()))
                    throw new AssertionError("retry reused unloaded resident given name despite unused configured names");
            }
            if (level.getEntity(household.slotId(0)) != null) throw new AssertionError("unloaded resident replaced");
            helper.succeed();
        } finally {
            field.set(null, originalFactory);
        }
    }
    private static void preparePopulationGround(GameTestHelper helper, BlockPos anchor, boolean blocked) {
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            helper.getLevel().setBlockAndUpdate(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) helper.getLevel().setBlockAndUpdate(anchor.offset(x, y, z),
                    (blocked ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
    }

    @GameTest
    public void naturalResidentKeepsAssignedSurnameAndHouseHome(GameTestHelper helper) {
        CitizenEntity resident = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        var houseId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000123");
        BlockPos entrance = helper.absolutePos(new BlockPos(6, 1, 6));
        resident.assignHousehold(houseId, "River", java.util.Set.of(), entrance);
        resident.tick();
        if (!resident.identity().familyName().equals("River")
                || !resident.getCustomName().getString().equals(resident.identity().displayName())
                || !resident.householdId().orElseThrow().equals(houseId)
                || !resident.homeSpot().orElseThrow().equals(entrance))
            throw new AssertionError("natural resident did not keep assigned household identity and home");
        helper.succeed();
    }

    @GameTest
    public void freshTerrainGenerationUsesRegisteredSpacingAndOverworldOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = level.registryAccess();
        var holder = registry.lookupOrThrow(Registries.STRUCTURE).getOrThrow(CitizenHouseStructures.HOUSE_KEY);
        var set = registry.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(net.minecraft.resources.ResourceKey.create(
                Registries.STRUCTURE_SET, net.minecraft.resources.Identifier.fromNamespaceAndPath("theeconomist", "citizen_houses"))).value();
        var placement = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement) set.placement();
        if (placement.spacing() != 16 || placement.separation() != 4) throw new AssertionError("wrong spacing");
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        var randomState = source.randomState();
        var climate = randomState.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
        var candidate = placement.getPotentialStructureChunk(level.getSeed(), 0, 0);
        var start = holder.value().generate(holder, net.minecraft.world.level.Level.OVERWORLD, registry, generator,
                generator.getBiomeSource(), climate, randomState, level.getStructureTemplateManager(), level.getSeed(),
                candidate, 0, level, holder.value().biomes()::contains);
        if (!start.isValid() || start.getPieces().size() != 1) throw new AssertionError("fresh flat land rejected");
        var piece = (CitizenHousePiece) start.getPieces().getFirst();
        if (!new net.minecraft.world.level.ChunkPos(piece.entrance().getX() >> 4, piece.entrance().getZ() >> 4).equals(candidate))
            throw new AssertionError("house anchor escaped start chunk");
        var rejected = holder.value().generate(holder, net.minecraft.world.level.Level.NETHER, registry, generator,
                generator.getBiomeSource(), climate, randomState, level.getStructureTemplateManager(), level.getSeed(),
                candidate, 0, level, biome -> true);
        if (rejected.isValid()) throw new AssertionError("generated outside Overworld");
        helper.succeed();
    }
    @GameTest(padding = 24)
    public void houseResourceAndRotatedEntranceSurviveReload(GameTestHelper helper) {
        var level = helper.getLevel();
        level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(CitizenHouseStructures.HOUSE_KEY);
        var manager = level.getStructureTemplateManager();
        var template = manager.get(CitizenHouseStructures.HOUSE_ID).orElseThrow();
        if (template.getSize().getX() != 9 || template.getSize().getZ() != 10)
            throw new AssertionError("wrong house footprint");
        for (Rotation rotation : Rotation.values()) {
            var piece = new CitizenHousePiece(manager, helper.absolutePos(new BlockPos(12, 2, 12)), rotation);
            if (!template.placeInWorld(level, piece.templatePosition(), piece.templatePosition(),
                    piece.placeSettings(), level.getRandom(), 2)) throw new AssertionError("template failed to place");
            if (!level.getBlockState(piece.entrance()).is(Blocks.OAK_DOOR)) throw new AssertionError("door missing");
            if (template.filterBlocks(piece.templatePosition(), piece.placeSettings(), Blocks.BED.white()).size() != 8)
                throw new AssertionError("four beds missing");
            for (var bed : template.filterBlocks(piece.templatePosition(), piece.placeSettings(), Blocks.BED.white())) {
                if (!level.getBlockState(bed.pos()).equals(bed.state()))
                    throw new AssertionError("placed bed state differs at " + bed.pos());
                if (!level.getBlockState(bed.pos().above()).isAir())
                    throw new AssertionError("bed has no headroom at " + bed.pos());
            }
            if (!level.getBlockState(piece.localToWorld(new BlockPos(4, 4, 3))).is(Blocks.OAK_PLANKS))
                throw new AssertionError("roof missing");
            if (!level.getBlockState(piece.localToWorld(new BlockPos(4, 0, 8))).is(Blocks.WATER))
                throw new AssertionError("outdoor water basin missing");
            var context = StructurePieceSerializationContext.fromLevel(level);
            var restored = new CitizenHousePiece(context, piece.createTag(context));
            if (!restored.entrance().equals(piece.entrance())) throw new AssertionError("entrance moved after reload");
        }
        helper.succeed();
    }
}





