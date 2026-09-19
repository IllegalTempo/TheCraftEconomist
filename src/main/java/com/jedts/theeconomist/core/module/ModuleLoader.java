package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates and initializes feature modules in their declared dependency order.
 */
public final class ModuleLoader {
    private ModuleLoader() {
    }

    public static void initialize(List<? extends TheEconomistModule> modules) {
        Objects.requireNonNull(modules, "modules");
        validate(modules);
        modules.forEach(TheEconomistModule::initialize);
    }

    private static void validate(List<? extends TheEconomistModule> modules) {
        Set<String> moduleIds = new HashSet<>();

        for (TheEconomistModule module : modules) {
            Objects.requireNonNull(module, "modules cannot contain null entries");
            String moduleId = module.id();

            if (moduleId == null || moduleId.isBlank()) {
                throw new IllegalArgumentException("Module IDs must not be blank");
            }

            if (!moduleIds.add(moduleId)) {
                throw new IllegalArgumentException("Duplicate module ID: " + moduleId);
            }
        }
    }
}
