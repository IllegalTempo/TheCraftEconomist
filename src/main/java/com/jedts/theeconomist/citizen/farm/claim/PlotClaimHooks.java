package com.jedts.theeconomist.citizen.farm.claim;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

public final class PlotClaimHooks {
    private PlotClaimHooks() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)
                    || hand != InteractionHand.MAIN_HAND || !isHoe(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            var pos = hit.getBlockPos();
            PlotClaimService claims = PlotClaimService.forLevel(level);
            if (level.getBlockState(pos).is(Blocks.FARMLAND)) {
                if (claims.ownerAt(pos).isEmpty() && claims.claimPlayerUse(serverPlayer, pos) > 0)
                    return InteractionResult.SUCCESS_SERVER;
                return InteractionResult.PASS;
            }
            InteractionResult result = player.getItemInHand(hand).useOn(new UseOnContext(player, hand, hit));
            if (level.getBlockState(pos).is(Blocks.FARMLAND)) {
                claims.claimNewPlayerFarmland(serverPlayer, pos);
            }
            return result;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel level && state.is(Blocks.FARMLAND))
                PlotClaimService.forLevel(level).remove(pos);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels())
                PlotClaimService.forLevel(level).reconcileLoaded(128);
        });
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.is(Items.WOODEN_HOE) || stack.is(Items.COPPER_HOE)
                || stack.is(Items.STONE_HOE) || stack.is(Items.GOLDEN_HOE)
                || stack.is(Items.IRON_HOE) || stack.is(Items.DIAMOND_HOE)
                || stack.is(Items.NETHERITE_HOE);
    }
}
