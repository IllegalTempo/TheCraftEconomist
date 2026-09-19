package com.jedts.theeconomist.citizen;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.command.CitizenCommands;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class CitizenModule implements TheEconomistModule {
    @Override
    public String id() {
        return "citizen";
    }

    @Override
    public void initialize() {
        CitizenRuntime.initialize();
        CitizenEntities.register();
        FabricDefaultAttributeRegistry.register(CitizenEntities.CITIZEN, com.jedts.theeconomist.citizen.entity.CitizenEntity.createAttributes());
        CitizenCommands.register();
    }
}
