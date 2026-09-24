package com.jedts.theeconomist.citizen;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorRegistry;
import com.jedts.theeconomist.citizen.behavior.ambient.IdleBehavior;
import com.jedts.theeconomist.citizen.behavior.ambient.LookAtCreatureBehavior;
import com.jedts.theeconomist.citizen.behavior.ambient.WanderBehavior;
import com.jedts.theeconomist.citizen.behavior.combat.CombatBehavior;
import com.jedts.theeconomist.citizen.behavior.emergency.AvoidMonsterBehavior;
import com.jedts.theeconomist.citizen.behavior.emergency.EscapeWaterBehavior;
import com.jedts.theeconomist.citizen.behavior.emergency.PanicBehavior;
import com.jedts.theeconomist.citizen.behavior.home.ReturnHomeBehavior;
import com.jedts.theeconomist.citizen.behavior.night.ConfusedBehavior;
import com.jedts.theeconomist.citizen.behavior.sleep.SleepBehavior;
import com.jedts.theeconomist.citizen.behavior.work.ComposterBehavior;
import com.jedts.theeconomist.citizen.behavior.work.ContractBehavior;
import com.jedts.theeconomist.citizen.behavior.work.FarmerBehavior;
import com.jedts.theeconomist.citizen.behavior.work.SeedFindingBehavior;
import com.jedts.theeconomist.citizen.command.CitizenCommands;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoRequestPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoNetworking;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimHooks;
import com.jedts.theeconomist.citizen.farm.conflict.LandConflictService;
import com.jedts.theeconomist.citizen.trade.CitizenTradeActionPayload;
import com.jedts.theeconomist.citizen.trade.CitizenTradeViewPayload;
import com.jedts.theeconomist.citizen.trade.CitizenTradeNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import java.util.Set;

public final class CitizenModule implements TheEconomistModule {
    @Override
    public String id() {
        return "citizen";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("currency");
    }

    static CitizenBehaviorRegistry createBehaviorRegistry() {
        return CitizenBehaviorRegistry.builder()
                .register("escape_water", EscapeWaterBehavior::new)
                .register("panic", PanicBehavior::new)
                .register("avoid_monster", AvoidMonsterBehavior::new)
                .register("combat", CombatBehavior::new)
                .register("sleep", SleepBehavior::new)
                .register("confused", ConfusedBehavior::new)
                .register("find_seeds", SeedFindingBehavior::new)
                .register("composter", ComposterBehavior::new)
                .register("farmer", FarmerBehavior::new)
                .register("contract", ContractBehavior::new)
                .register("return_home", ReturnHomeBehavior::new)
                .register("look_at_creature", LookAtCreatureBehavior::new)
                .register("wander", WanderBehavior::new)
                .register("idle", IdleBehavior::new)
                .build();
    }

    @Override
    public void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(ContractBoardPayload.TYPE, ContractBoardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CitizenInfoPayload.TYPE, CitizenInfoPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CitizenInfoRequestPayload.TYPE, CitizenInfoRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CitizenTradeActionPayload.TYPE, CitizenTradeActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CitizenTradeViewPayload.TYPE, CitizenTradeViewPayload.CODEC);
        CitizenRuntime.initialize(createBehaviorRegistry());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CitizenRuntime.contracts().load(server.overworld()));
        CitizenEntities.register();
        com.jedts.theeconomist.citizen.house.CitizenHouseStructures.register();
        com.jedts.theeconomist.citizen.house.HousePopulationService.register();
        FabricDefaultAttributeRegistry.register(CitizenEntities.CITIZEN, com.jedts.theeconomist.citizen.entity.CitizenEntity.createAttributes());
        CitizenCommands.register();
        PlotClaimHooks.register();
        LandConflictService.register();
        CitizenTradeNetworking.register();
        CitizenInfoNetworking.register();
    }
}

