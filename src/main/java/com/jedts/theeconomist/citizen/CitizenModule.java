package com.jedts.theeconomist.citizen;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.command.CitizenCommands;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class CitizenModule implements TheEconomistModule {
    @Override
    public String id() {
        return "citizen";
    }

    @Override
    public void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(ContractBoardPayload.TYPE, ContractBoardPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CitizenInfoPayload.TYPE, CitizenInfoPayload.CODEC);
        CitizenRuntime.initialize();
        CitizenEntities.register();
        FabricDefaultAttributeRegistry.register(CitizenEntities.CITIZEN, com.jedts.theeconomist.citizen.entity.CitizenEntity.createAttributes());
        CitizenCommands.register();
    }
}
