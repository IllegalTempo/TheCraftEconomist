package com.jedts.theeconomist;

import com.jedts.theeconomist.core.module.ModModules;
import com.jedts.theeconomist.core.module.ModuleLoader;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point for The Economist.
 */
public final class TheEconomistMod implements ModInitializer {
    public static final String MOD_ID = "theeconomist";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModuleLoader.initialize(ModModules.all());
        LOGGER.info("The Economist foundation loaded with {} feature modules.", ModModules.all().size());
    }
}
