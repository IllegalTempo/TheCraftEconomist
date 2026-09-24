package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Sends refreshed server-authoritative CitizenInfo data to an open client screen. */
public final class CitizenInfoNetworking {
    private CitizenInfoNetworking() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CitizenInfoRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (!(context.player().level().getEntity(payload.entityId()) instanceof CitizenEntity citizen)
                            || !citizen.isAlive() || context.player().distanceToSqr(citizen) > 36.0) return;
                    send(context.player(), citizen);
                }));
    }

    public static void send(ServerPlayer player, CitizenEntity citizen) {
        var contract = com.jedts.theeconomist.citizen.CitizenRuntime.contracts().activeContractFor(citizen.getUUID());
        String status = contract.map(value -> value.status().name()).orElse("NONE");
        String target = contract.map(value -> value.target()).orElse("");
        int bounty = contract.map(value -> value.bounty()).orElse(0);
        long deadline = contract.map(value -> value.deadlineTick()).orElse(0L);
        var job = citizen.job();
        ServerPlayNetworking.send(player, new CitizenInfoPayload(citizen.getId(), job.occupation().name(),
                job.employer(), job.wagePerDay(), job.startHour() + "-" + job.endHour(), status, target, bounty, deadline,
                CitizenOverview.from(citizen), PlotClaimService.forLevel(player.level()).infoDetails(citizen),
                citizen.latestBehaviorEvaluations().stream().map(CitizenDecisionView::from).toList(),
                citizen.activeCraftingPreview().orElse(null)));
    }
}
