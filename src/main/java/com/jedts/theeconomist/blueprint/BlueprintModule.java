package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.api.module.TheEconomistModule;

public final class BlueprintModule implements TheEconomistModule {
    @Override public String id() { return "blueprint"; }
    @Override public void initialize() { BlueprintItems.register(); }
}
