package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;

import java.util.List;

/**
 * The ordered inventory of enabled feature modules.
 *
 * <p>Add future modules here in dependency order. The foundation intentionally
 * starts with no gameplay modules.</p>
 */
public final class ModModules {
    private static final List<TheEconomistModule> MODULES = List.of();

    private ModModules() {
    }

    public static List<TheEconomistModule> all() {
        return MODULES;
    }
}
