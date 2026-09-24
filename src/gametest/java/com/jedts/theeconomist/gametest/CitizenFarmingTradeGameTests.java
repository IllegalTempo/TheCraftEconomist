package com.jedts.theeconomist.gametest;

import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.farm.FarmerWorkService;
import com.jedts.theeconomist.citizen.farm.NaturalWater;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.info.CitizenOverview;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import com.jedts.theeconomist.citizen.trade.CitizenTradeAction;
import com.jedts.theeconomist.citizen.trade.CitizenTradeResult;
import com.jedts.theeconomist.citizen.trade.CitizenTradeService;
import com.jedts.theeconomist.citizen.trade.CitizenTradeNetworking;
import com.jedts.theeconomist.citizen.trade.CitizenTradeActionPayload;
import com.jedts.theeconomist.citizen.trade.CitizenTradeTransaction;
import com.jedts.theeconomist.citizen.trade.CoinDenominations;
import com.jedts.theeconomist.citizen.trade.TradeInventory;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class CitizenFarmingTradeGameTests {
    @GameTest
    public void citizenStartsWithHoeAndTouchingOwnersRemainSeparate(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.FARMLAND);
        helper.setBlock(2, 1, 1, Blocks.FARMLAND);
        BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 1));
        var claims = PlotClaimService.forLevel(helper.getLevel());
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(1, 2, 1));
        CitizenEntity other = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 1));
        claims.claimNewCitizenFarmland(citizen, first);
        claims.claimNewCitizenFarmland(other, second);
        if (!claims.ownerAt(first).orElseThrow().id().equals(citizen.getUUID())
                || !claims.ownerAt(second).orElseThrow().id().equals(other.getUUID())
                || claims.component(first).size() != 1) throw new AssertionError("touching claims merged");
        if (citizen.farmInventory().count(Items.WOODEN_HOE) != 1)
            throw new AssertionError("Citizen did not start with a hoe");
        helper.succeed();
    }

    @GameTest
    public void citizenHarvestsRealWheatIntoInventory(GameTestHelper helper) {
        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND.defaultBlockState());
        helper.setBlock(farm.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        PlotClaimService.forLevel(helper.getLevel()).claimNewCitizenFarmland(citizen, helper.absolutePos(farm));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30 && citizen.farmInventory().count(Items.WHEAT) == 0; i++) work.tick(citizen);
        if (citizen.farmInventory().count(Items.WHEAT) == 0)
            throw new AssertionError("Citizen did not harvest real wheat: center=" + citizen.blockPosition()
                    + ", farm=" + helper.absolutePos(farm) + ", crop=" + helper.getLevel().getBlockState(helper.absolutePos(farm.above()))
                    + ", height=" + helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    helper.absolutePos(farm).getX(), helper.absolutePos(farm).getZ()));
        helper.succeed();
    }

    @GameTest
    public void physicalCrownsBuyOnlySurplusWheat(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT, 5));
        CoinDenominations crowns = CoinDenominations.registered();
        List<ItemStack> player = new ArrayList<>();
        for (int i = 0; i < 36; i++) player.add(ItemStack.EMPTY);
        player.set(0, new ItemStack(crowns.copper(), 4));
        var outcome = CitizenTradeTransaction.evaluate(new TradeInventory(player),
                new TradeInventory(citizen.farmInventory().stacks()), crowns, citizen.priceSnapshot(),
                CitizenTradeAction.BUY_WHEAT, 1);
        if (outcome.result() != CitizenTradeResult.SUCCESS
                || new TradeInventory(outcome.playerItems()).count(Items.WHEAT) != 1
                || new TradeInventory(outcome.citizenItems()).count(Items.WHEAT) != 4
                || new TradeInventory(outcome.citizenItems()).coins(crowns).value() != 4)
            throw new AssertionError("physical Crown wheat transaction failed");
        helper.succeed();
    }

    @GameTest
    public void serverTradeMovesCoinsOnceAndRejectsReplay(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT, 5));
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos near = helper.absolutePos(new BlockPos(3, 2, 2));
        player.teleportTo(near.getX(), near.getY(), near.getZ());
        CoinDenominations crowns = CoinDenominations.registered();
        player.getInventory().setItem(0, new ItemStack(crowns.copper(), 4));
        CitizenTradeService service = new CitizenTradeService();
        UUID request = UUID.randomUUID();
        var first = service.execute(player, citizen, CitizenTradeAction.BUY_WHEAT, 1,
                citizen.priceSnapshot().version(), request);
        int wheat = player.getInventory().countItem(Items.WHEAT);
        var second = service.execute(player, citizen, CitizenTradeAction.BUY_WHEAT, 1,
                citizen.priceSnapshot().version(), request);
        if (first != CitizenTradeResult.SUCCESS || second != CitizenTradeResult.DUPLICATE || wheat != 1
                || player.getInventory().countItem(Items.WHEAT) != 1
                || citizen.farmInventory().count(Items.WHEAT) != 4)
            throw new AssertionError("trade was not exactly once: " + first + ", " + second);
        helper.succeed();
    }

    @GameTest
    public void playerHarvestPreservesCitizenClaim(GameTestHelper helper) {
        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND.defaultBlockState());
        helper.setBlock(farm.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        BlockPos absoluteFarm = helper.absolutePos(farm);
        var claims = PlotClaimService.forLevel(helper.getLevel());
        claims.claimNewCitizenFarmland(citizen, absoluteFarm);
        var player = helper.makeMockServerPlayerInLevel();
        helper.getLevel().destroyBlock(absoluteFarm.above(), true, player);
        if (!helper.getLevel().getBlockState(absoluteFarm.above()).isAir()
                || !claims.ownerAt(absoluteFarm).orElseThrow().id().equals(citizen.getUUID()))
            throw new AssertionError("normal player harvest changed Citizen ownership");
        helper.succeed();
    }

    @GameTest
    public void playerHoeClaimsUnownedFarmland(GameTestHelper helper) {
        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_HOE));
        BlockPos absoluteFarm = helper.absolutePos(farm);
        player.teleportTo(absoluteFarm.getX(), absoluteFarm.getY() + 1, absoluteFarm.getZ());
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absoluteFarm),
                        net.minecraft.core.Direction.UP, absoluteFarm, false));
        var owner = PlotClaimService.forLevel(helper.getLevel()).ownerAt(absoluteFarm);
        if (owner.isEmpty() || !owner.orElseThrow().id().equals(player.getUUID()))
            throw new AssertionError("player hoe did not claim unowned farmland");
        helper.succeed();
    }

    @GameTest
    public void deliberateChallengeNeverTransfersOwnership(GameTestHelper helper) {
        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND.defaultBlockState());
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        BlockPos absoluteFarm = helper.absolutePos(farm);
        var claims = PlotClaimService.forLevel(helper.getLevel());
        claims.claimNewCitizenFarmland(citizen, absoluteFarm);
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_HOE));
        player.teleportTo(absoluteFarm.getX(), absoluteFarm.getY() + 1, absoluteFarm.getZ());
        player.setShiftKeyDown(true);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absoluteFarm),
                        net.minecraft.core.Direction.UP, absoluteFarm, false));
        if (!claims.ownerAt(absoluteFarm).orElseThrow().id().equals(citizen.getUUID()))
            throw new AssertionError("challenge transferred land");
        helper.succeed();
    }

    @GameTest
    public void citizenDeathReleasesClaim(GameTestHelper helper) {
        BlockPos farm = new BlockPos(2, 1, 2);
        helper.setBlock(farm, Blocks.FARMLAND.defaultBlockState());
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        BlockPos absoluteFarm = helper.absolutePos(farm);
        var claims = PlotClaimService.forLevel(helper.getLevel());
        claims.claimNewCitizenFarmland(citizen, absoluteFarm);
        citizen.die(helper.getLevel().damageSources().generic());
        if (claims.ownerAt(absoluteFarm).isPresent())
            throw new AssertionError("dead Citizen retained a claim");
        helper.succeed();
    }

    @GameTest
    public void repeatedOpenCannotReplayTradeRequest(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT, 6));
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos near = helper.absolutePos(new BlockPos(3, 2, 2));
        player.teleportTo(near.getX(), near.getY(), near.getZ());
        player.getInventory().setItem(0, new ItemStack(CoinDenominations.registered().copper(), 8));
        UUID request = UUID.randomUUID();
        var open = new CitizenTradeActionPayload(citizen.getId(), CitizenTradeAction.OPEN.ordinal(), 0, 0,
                UUID.randomUUID());
        var buy = new CitizenTradeActionPayload(citizen.getId(), CitizenTradeAction.BUY_WHEAT.ordinal(), 1,
                citizen.priceSnapshot().version(), request);
        CitizenTradeNetworking.handle(player, open);
        CitizenTradeNetworking.handle(player, buy);
        CitizenTradeNetworking.handle(player, open);
        CitizenTradeNetworking.handle(player, buy);
        CitizenTradeNetworking.close(player);
        if (player.getInventory().countItem(Items.WHEAT) != 1 || citizen.farmInventory().count(Items.WHEAT) != 5)
            throw new AssertionError("reopening reset trade replay protection");
        helper.succeed();
    }

    @GameTest
    public void citizenInfoPacketCarriesInventoryAndWorkSnapshot(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT, 9));
        helper.setBlock(2, 1, 2, Blocks.FARMLAND);
        var claims = PlotClaimService.forLevel(helper.getLevel());
        claims.claimNewCitizenFarmland(citizen, helper.absolutePos(new BlockPos(2, 1, 2)));
        var details = claims.infoDetails(citizen);
        var decision = new com.jedts.theeconomist.citizen.info.CitizenDecisionView("farmer", "Farm", true,
                72.5, false, "field needs tending", List.of(new com.jedts.theeconomist.citizen.behavior.decision.CitizenDecisionFactor("opportunity", .8, 8)), true, true);
        var original = new CitizenInfoPayload(citizen.getId(), "FARMER", "", 0, "0-8",
                "NONE", "x".repeat(300), 0, 0, CitizenOverview.from(citizen), details, List.of(decision));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        CitizenInfoPayload.CODEC.encode(buffer, original);
        var decoded = CitizenInfoPayload.CODEC.decode(buffer);
        if (decoded.details().inventory().size() != 27
                || decoded.details().claimedFarmland() != 1
                || decoded.details().sellableWheat() != 5
                || new TradeInventory(decoded.details().inventory()).count(Items.WHEAT) != 9
                || decoded.details().farmStatus().isBlank()
                || !decoded.overview().name().equals(citizen.identity().displayName())
                || decoded.contractTarget().length() != 256 || decoded.decisions().size() != 1
                || !decoded.decisions().getFirst().actionId().equals("farmer")
                || decoded.decisions().getFirst().score() != 72.5
                || !decoded.decisions().getFirst().factors().getFirst().id().equals("opportunity"))
            throw new AssertionError("Citizen info packet lost work, inventory, or decision data");
        helper.succeed();
    }

    @GameTest
    public void seedlessCitizenBreaksNearbyGrassForSeeds(GameTestHelper helper) {
        BlockPos grass = new BlockPos(2, 2, 2);
        helper.setBlock(grass.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(grass, Blocks.SHORT_GRASS);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30 && helper.getLevel().getBlockState(helper.absolutePos(grass)).is(Blocks.SHORT_GRASS); i++)
            work.tick(citizen);
        if (helper.getLevel().getBlockState(helper.absolutePos(grass)).is(Blocks.SHORT_GRASS)) {
            BlockPos absolute = helper.absolutePos(grass);
            throw new AssertionError("Citizen ignored grass: height=" + helper.getLevel().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    absolute.getX(), absolute.getZ()) + ", citizen=" + citizen.blockPosition());
        }
        helper.succeed();
    }

    @GameTest
    public void shortGrassLootCanYieldWheatSeeds(GameTestHelper helper) {
        BlockPos grass = new BlockPos(2, 2, 2);
        helper.setBlock(grass.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(grass, Blocks.SHORT_GRASS);
        BlockPos absolute = helper.absolutePos(grass);
        int seeds = 0;
        for (int i = 0; i < 100; i++)
            for (ItemStack drop : Block.getDrops(helper.getLevel().getBlockState(absolute),
                    helper.getLevel(), absolute, null))
                if (drop.is(Items.WHEAT_SEEDS)) seeds += drop.getCount();
        if (seeds == 0) throw new AssertionError("short grass block drops never yielded wheat seeds");
        helper.succeed();
    }

    @GameTest
    public void fernLootCanYieldWheatSeeds(GameTestHelper helper) {
        BlockPos fern = new BlockPos(2, 2, 2);
        helper.setBlock(fern.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(fern, Blocks.FERN);
        BlockPos absolute = helper.absolutePos(fern);
        int seeds = 0;
        for (int i = 0; i < 100; i++)
            for (ItemStack drop : Block.getDrops(helper.getLevel().getBlockState(absolute),
                    helper.getLevel(), absolute, null))
                if (drop.is(Items.WHEAT_SEEDS)) seeds += drop.getCount();
        if (seeds == 0) throw new AssertionError("fern block drops never yielded wheat seeds");
        helper.succeed();
    }

    @GameTest
    public void seedlessCitizenBreaksNearbyFernForSeeds(GameTestHelper helper) {
        BlockPos fern = new BlockPos(2, 2, 2);
        helper.setBlock(fern.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(fern, Blocks.FERN);
        BlockPos absolute = helper.absolutePos(fern);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30 && helper.getLevel().getBlockState(absolute).is(Blocks.FERN); i++) work.tick(citizen);
        if (helper.getLevel().getBlockState(absolute).is(Blocks.FERN))
            throw new AssertionError("Citizen ignored a nearby fern");
        helper.succeed();
    }

    @GameTest
    public void gatheredSeedsSurviveInventorySaveLoad(GameTestHelper helper) {
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        inventory.insert(new ItemStack(Items.WHEAT_SEEDS, 3));
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        inventory.write(output);
        CitizenFarmInventory restored = CitizenFarmInventory.read(TagValueInput.create(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        if (restored.count(Items.WHEAT_SEEDS) != 3)
            throw new AssertionError("wheat seeds were lost during inventory save/load");
        helper.succeed();
    }

    @GameTest
    public void seedlessCitizenSearchesBeforeHoeingNewGround(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(2, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30; i++) work.tick(citizen);
        if (!helper.getLevel().getBlockState(helper.absolutePos(dirt)).is(Blocks.DIRT))
            throw new AssertionError("Citizen hoed new ground without seeds instead of foraging");
        helper.succeed();
    }

    @GameTest
    public void citizenSwingsWhenBreakingSeedGrass(GameTestHelper helper) {
        BlockPos grass = new BlockPos(2, 2, 2);
        helper.setBlock(grass.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(grass, Blocks.SHORT_GRASS);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30 && helper.getLevel().getBlockState(helper.absolutePos(grass)).is(Blocks.SHORT_GRASS); i++)
            work.tick(citizen);
        if (!helper.getLevel().getBlockState(helper.absolutePos(grass)).isAir() || !citizen.isSwinging())
            throw new AssertionError("Citizen broke seed grass without a vanilla arm swing");
        helper.succeed();
    }

    @GameTest
    public void citizenSwingsWhenHoeingGround(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(2, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(dirt.east(), Blocks.WATER);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30 && helper.getLevel().getBlockState(helper.absolutePos(dirt)).is(Blocks.DIRT); i++)
            work.tick(citizen);
        if (!helper.getLevel().getBlockState(helper.absolutePos(dirt)).is(Blocks.FARMLAND))
            throw new AssertionError("Citizen did not hoe ground");
        if (!citizen.isSwinging())
            throw new AssertionError("Citizen hoed ground without a vanilla arm swing");
        helper.succeed();
    }

    @GameTest
    public void citizenShowsOnlyItsOwnedHoeInHand(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        if (!citizen.getMainHandItem().is(Items.WOODEN_HOE))
            throw new AssertionError("Citizen's farming hoe was invisible in the vanilla hand renderer");
        citizen.farmInventory().remove(Items.WOODEN_HOE, 1);
        citizen.tick();
        if (!citizen.getMainHandItem().isEmpty())
            throw new AssertionError("Citizen kept showing a hoe after its inventory lost the tool");
        helper.succeed();
    }

    @GameTest
    public void visibleHoeDoesNotDuplicateWhenCitizenDies(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        var nearby = citizen.getBoundingBox().inflate(4);
        citizen.die(helper.getLevel().damageSources().generic());
        int hoes = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby)
                .stream().filter(item -> item.getItem().is(Items.WOODEN_HOE))
                .mapToInt(item -> item.getItem().getCount()).sum();
        if (hoes != 1) throw new AssertionError("visible hoe duplicated or vanished on death: " + hoes);
        helper.succeed();
    }

    @GameTest
    public void citizenKeepsItsChosenHomeAfterMovingAndSaving(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow(() -> new AssertionError("Citizen did not settle"));
        citizen.setPos(citizen.getX() + 10, citizen.getY(), citizen.getZ());
        citizen.tick();
        if (!citizen.homeSpot().orElseThrow().equals(home))
            throw new AssertionError("Citizen moved its home while wandering");
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        citizen.saveWithoutId(output);
        CitizenEntity restored = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), output.buildResult()));
        if (!restored.homeSpot().orElseThrow().equals(home))
            throw new AssertionError("Citizen home was lost on save/load");
        helper.succeed();
    }

    @GameTest
    public void citizenDoesNotStartFarmAtWanderedPosition(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow();
        BlockPos farGround = home.offset(19, -1, 0);
        helper.getLevel().setBlock(farGround, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(farGround.above(), Blocks.AIR.defaultBlockState(), 3);
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        citizen.setPos(farGround.getX() + 0.5, farGround.getY() + 1, farGround.getZ() + 0.5);
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30; i++) work.tick(citizen);
        if (!helper.getLevel().getBlockState(farGround).is(Blocks.DIRT))
            throw new AssertionError("Citizen made farmland around its wandered position");
        helper.succeed();
    }

    @GameTest
    public void neighboringCitizensChooseDifferentHomeSpots(GameTestHelper helper) {
        CitizenEntity first = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        CitizenEntity second = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        first.tick();
        second.tick();
        BlockPos a = first.homeSpot().orElseThrow();
        BlockPos b = second.homeSpot().orElseThrow();
        if (a.distSqr(b) < 16) throw new AssertionError("Citizens settled on top of each other");
        helper.succeed();
    }

    @GameTest
    public void citizenLeavesHomeSpotUnhoed(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos homeGround = citizen.homeSpot().orElseThrow().below();
        helper.getLevel().setBlock(homeGround, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(homeGround.above(), Blocks.AIR.defaultBlockState(), 3);
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 30; i++) work.tick(citizen);
        if (!helper.getLevel().getBlockState(homeGround).is(Blocks.DIRT))
            throw new AssertionError("Citizen hoed its marked home spot");
        helper.succeed();
    }

    @GameTest
    public void citizenInfoPacketShowsSettledHome(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow();
        var details = PlotClaimService.forLevel(helper.getLevel()).infoDetails(citizen);
        var original = new CitizenInfoPayload(citizen.getId(), "FARMER", "", 0, "0-8",
                "NONE", "", 0, 0, CitizenOverview.from(citizen), details);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        CitizenInfoPayload.CODEC.encode(buffer, original);
        var decoded = CitizenInfoPayload.CODEC.decode(buffer);
        String expected = home.getX() + ", " + home.getY() + ", " + home.getZ();
        if (!decoded.details().home().equals(expected))
            throw new AssertionError("Citizen info packet lost the saved home location");
        helper.succeed();
    }

    @GameTest
    public void citizenChoosesDryHomeSpot(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        helper.setBlock(2, 2, 2, Blocks.WATER);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow();
        if (!helper.getLevel().getFluidState(home).isEmpty())
            throw new AssertionError("Citizen settled in water despite dry ground nearby");
        helper.succeed();
    }

    @GameTest
    public void newCitizenHasHoeButNoSignAndNeverPlacesOne(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++) helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        if (citizen.farmInventory().count(Items.WOODEN_HOE) != 1
                || citizen.farmInventory().count(Items.OAK_SIGN) != 0)
            throw new AssertionError("new Citizen should start with a hoe and no sign");
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow();
        citizen.tick();
        if (!helper.getLevel().getBlockState(home).isAir()
                || citizen.farmInventory().count(Items.OAK_SIGN) != 0)
            throw new AssertionError("Citizen created a home sign");
        helper.succeed();
    }

    @GameTest
    public void oldSignItemRemainsAnOrdinaryInventoryItem(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.farmInventory().insert(new ItemStack(Items.OAK_SIGN));
        BlockPos home = citizen.settleHome(helper.getLevel()).orElseThrow();
        citizen.tick();
        if (!helper.getLevel().getBlockState(home).isAir()
                || citizen.farmInventory().count(Items.OAK_SIGN) != 1)
            throw new AssertionError("Citizen changed its existing sign item or placed it");
        helper.succeed();
    }

    @GameTest
    public void olderCitizenSaveWithFalseSignFlagKeepsNameAndHomeWithoutAddingSign(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        BlockPos savedHome = new BlockPos(6, 1, 6);
        UUID householdId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        citizen.assignHousehold(householdId, "River", java.util.Set.of(), savedHome);
        String savedName = citizen.identity().displayName();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        citizen.saveWithoutId(output);
        var oldSave = output.buildResult();
        oldSave.getCompoundOrEmpty("TheEconomistCitizen").putBoolean("HomeSignIssued", false);
        CitizenEntity restored = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), oldSave));
        if (!restored.identity().displayName().equals(savedName)
                || !restored.homeSpot().orElseThrow().equals(savedHome)
                || !restored.householdId().orElseThrow().equals(householdId)
                || restored.farmInventory().count(Items.OAK_SIGN) != 0)
            throw new AssertionError("old save migration changed Citizen identity/home or added a sign");
        helper.succeed();
    }

    @GameTest
    public void citizenFacesSeedGrassBeforeBreakingIt(GameTestHelper helper) {
        BlockPos grass = new BlockPos(2, 2, 2);
        helper.setBlock(grass.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(grass, Blocks.SHORT_GRASS);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        FarmerWorkService work = new FarmerWorkService();
        work.tick(citizen);
        BlockPos absolute = helper.absolutePos(grass);
        if (!helper.getLevel().getBlockState(absolute).is(Blocks.SHORT_GRASS))
            throw new AssertionError("Citizen broke the block before facing it");
        assertLookingAt(citizen, absolute);
        work.tick(citizen);
        if (!helper.getLevel().getBlockState(absolute).isAir())
            throw new AssertionError("Citizen did not break the block after facing it");
        helper.succeed();
    }

    @GameTest
    public void citizenFacesGroundBeforeHoeingIt(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(2, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 3));
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 10 && !work.status().equals("Preparing farm work"); i++) work.tick(citizen);
        BlockPos absolute = helper.absolutePos(dirt);
        if (!helper.getLevel().getBlockState(absolute).is(Blocks.DIRT))
            throw new AssertionError("Citizen hoed the block before facing it");
        if (!work.status().equals("Preparing farm work"))
            throw new AssertionError("Citizen did not find dry ground after scanning for watered land");
        assertLookingAt(citizen, absolute);
        work.tick(citizen);
        if (!helper.getLevel().getBlockState(absolute).is(Blocks.FARMLAND))
            throw new AssertionError("Citizen did not hoe the block after facing it");
        helper.succeed();
    }

    @GameTest
    public void citizenDoesNotTurnToPlaceAHomeSign(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++) helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        citizen.tick();
        BlockPos home = citizen.homeSpot().orElseThrow();
        citizen.tick();
        if (!helper.getLevel().getBlockState(home).isAir()
                || citizen.farmInventory().count(Items.OAK_SIGN) != 0)
            throw new AssertionError("Citizen placed a sign at its home");
        helper.succeed();
    }

    private static void assertLookingAt(CitizenEntity citizen, BlockPos block) {
        var toward = net.minecraft.world.phys.Vec3.atCenterOf(block).subtract(citizen.getEyePosition()).normalize();
        if (citizen.getLookAngle().dot(toward) < 0.98)
            throw new AssertionError("Citizen is not facing its interaction block");
    }

    @GameTest
    public void citizenSettlesCloserToNaturalWater(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++)
            for (int z = 1; z <= 3; z++) helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        helper.setBlock(10, 1, 2, Blocks.SAND);
        helper.setBlock(11, 1, 2, Blocks.WATER);
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        BlockPos home = citizen.settleHome(helper.getLevel()).orElseThrow();
        if (home.getX() < helper.absolutePos(new BlockPos(3, 2, 2)).getX())
            throw new AssertionError("Citizen chose closer dry ground instead of a home near natural water");
        helper.succeed();
    }

    @GameTest(padding = 24)
    public void citizenHoesHydratedGroundBeforeNearbyDryGround(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        BlockPos home = citizen.settleHome(helper.getLevel()).orElseThrow();
        BlockPos dry = home.offset(-1, -1, 0);
        BlockPos hydrated = home.offset(1, -1, 0);
        BlockPos shore = home.offset(4, -1, 0);
        BlockPos water = home.offset(5, -1, 0);
        helper.getLevel().setBlock(dry, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(hydrated, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(shore, Blocks.SAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        helper.getLevel().setBlock(dry.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(hydrated.above(), Blocks.AIR.defaultBlockState(), 3);
        if (NaturalWater.hydrates(helper.getLevel(), dry))
            throw new AssertionError("Test fixture left the dry target within water hydration range");
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        FarmerWorkService work = new FarmerWorkService();
        for (int i = 0; i < 8 && !helper.getLevel().getBlockState(hydrated).is(Blocks.FARMLAND); i++) work.tick(citizen);
        if (!helper.getLevel().getBlockState(hydrated).is(Blocks.FARMLAND)
                || !helper.getLevel().getBlockState(dry).is(Blocks.DIRT))
            throw new AssertionError("Citizen hoed dry ground before land within water hydration range");
        helper.succeed();
    }

    @GameTest
    public void citizenWaitsForWateredLandBeyondFirstScanBatch(GameTestHelper helper) {
        CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
        BlockPos home = citizen.settleHome(helper.getLevel()).orElseThrow();
        BlockPos dry = home.offset(1, -1, 0);
        BlockPos hydrated = home.offset(7, -1, 0);
        BlockPos shore = home.offset(10, -1, 0);
        BlockPos water = home.offset(11, -1, 0);
        helper.getLevel().setBlock(dry, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(hydrated, Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(shore, Blocks.SAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        helper.getLevel().setBlock(dry.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(hydrated.above(), Blocks.AIR.defaultBlockState(), 3);
        citizen.farmInventory().insert(new ItemStack(Items.WHEAT_SEEDS, 2));
        FarmerWorkService work = new FarmerWorkService();
        work.tick(citizen);
        work.tick(citizen);
        if (!helper.getLevel().getBlockState(dry).is(Blocks.DIRT))
            throw new AssertionError("Citizen hoed nearby dry ground before scanning the watered side of its home");
        citizen.setPos(hydrated.getX() + 0.5, hydrated.getY() + 1, hydrated.getZ() + 0.5);
        for (int i = 0; i < 10 && !helper.getLevel().getBlockState(hydrated).is(Blocks.FARMLAND); i++)
            work.tick(citizen);
        if (!helper.getLevel().getBlockState(hydrated).is(Blocks.FARMLAND)
                || !helper.getLevel().getBlockState(dry).is(Blocks.DIRT))
            throw new AssertionError("Citizen did not prefer watered ground after reaching it in the scan");
        helper.succeed();
    }
}
