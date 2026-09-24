package com.jedts.theeconomist.citizen.farm.conflict;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimHooks;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import com.jedts.theeconomist.citizen.farm.claim.PlotOwner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Creates one bounded combat incident per deliberate land challenge. */
public final class LandConflictService {
    private static final ChallengeCooldown COOLDOWN = new ChallengeCooldown();
    private static final Map<UUID, Incident> ACTIVE = new HashMap<>();

    private LandConflictService() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer challenger)
                    || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                    || !PlotClaimHooks.isHoe(player.getItemInHand(hand))) return InteractionResult.PASS;
            PlotClaimService claims = PlotClaimService.forLevel(level);
            PlotOwner owner = claims.ownerAt(hit.getBlockPos()).orElse(null);
            if (owner == null || (owner.kind() != PlotOwner.Kind.CITIZEN
                    && owner.kind() != PlotOwner.Kind.HOUSEHOLD)) return InteractionResult.PASS;
            if (challenger.isCreative() || challenger.isSpectator() || !challenger.isAlive()) return InteractionResult.PASS;
            CitizenEntity citizen;
            if (owner.kind() == PlotOwner.Kind.CITIZEN) {
                if (!(level.getEntity(owner.id()) instanceof CitizenEntity individual)) return InteractionResult.PASS;
                citizen = individual;
            } else {
                citizen = level.getEntitiesOfClass(CitizenEntity.class, new AABB(hit.getBlockPos()).inflate(16),
                                candidate -> candidate.isAlive() && claims.ownerFor(candidate).equals(owner)).stream()
                        .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(challenger))).orElse(null);
                if (citizen == null) return InteractionResult.PASS;
            }
            if (citizen.distanceToSqr(challenger) > 256.0) return InteractionResult.PASS;
            challenge(citizen, challenger, hit.getBlockPos());
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ACTIVE.entrySet().removeIf(entry -> {
                Incident incident = entry.getValue();
                CitizenEntity citizen = incident.citizen();
                LivingEntity opponent = incident.opponent();
                boolean over = !citizen.isAlive() || !opponent.isAlive() || citizen.level() != opponent.level()
                        || citizen.level().getGameTime() >= incident.endTick()
                        || citizen.distanceToSqr(opponent) > 256.0
                        || citizen.getHealth() < citizen.getMaxHealth() * 0.25
                        || opponent.getHealth() <= Math.max(2.0f, opponent.getMaxHealth() * 0.25f);
                if (over) citizen.setTarget(null);
                return over;
            });
        });
    }

    public static boolean challenge(CitizenEntity citizen, LivingEntity challenger, BlockPos claimedPos) {
        if (!(citizen.level() instanceof ServerLevel level) || !citizen.isAlive() || !challenger.isAlive()
                || citizen.level() != challenger.level() || citizen.distanceToSqr(challenger) > 256.0) return false;
        if (challenger.getHealth() <= Math.max(2.0f, challenger.getMaxHealth() * 0.25f)) return false;
        if (challenger instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) return false;
        PlotClaimService claims = PlotClaimService.forLevel(level);
        PlotOwner owner = claims.ensureCitizenClaimsMigrated(citizen);
        if (!claims.ownerAt(claimedPos).filter(owner::equals).isPresent()) return false;
        var component = claims.component(claimedPos);
        BlockPos anchor = component.stream().min(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ())).orElse(claimedPos);
        if (!COOLDOWN.tryStart(citizen.getUUID(), challenger.getUUID(), anchor, level.getGameTime())) return false;
        double chance = LandConflictRules.fightChance(citizen.stats().anger(), component.size(),
                unclaimedNearby(level, claims, anchor), citizen.getHealth(), challenger.getHealth(),
                citizen.getArmorValue(), challenger.getArmorValue(), citizen.priceSnapshot().wheatPrice());
        boolean fight = level.getRandom().nextDouble() < chance;
        if (fight) {
            citizen.setTarget(challenger);
            ACTIVE.put(citizen.getUUID(), new Incident(citizen, challenger, level.getGameTime() + 200));
        }
        if (challenger instanceof ServerPlayer player) player.sendSystemMessage(Component.literal(fight
                ? citizen.getName().getString() + " is defending this farm."
                : citizen.getName().getString() + " declines the challenge."));
        return fight;
    }

    private static int unclaimedNearby(ServerLevel level, PlotClaimService claims, BlockPos center) {
        int count = 0;
        for (int x = center.getX() - 16; x <= center.getX() + 16 && count < 64; x++) {
            for (int z = center.getZ() - 16; z <= center.getZ() + 16 && count < 64; z++) {
                BlockPos probe = new BlockPos(x, center.getY(), z);
                if (!level.hasChunkAt(probe)) continue;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                var state = level.getBlockState(ground);
                if (claims.ownerAt(ground).isEmpty() && (state.is(Blocks.FARMLAND)
                        || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK))) count++;
            }
        }
        return count;
    }

    private record Incident(CitizenEntity citizen, LivingEntity opponent, long endTick) { }
}
