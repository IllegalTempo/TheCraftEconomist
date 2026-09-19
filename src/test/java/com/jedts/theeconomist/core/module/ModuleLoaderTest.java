package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModuleLoaderTest {
    @Test
    void initializesModulesInDeclarationOrder() {
        List<String> initializationOrder = new ArrayList<>();
        List<TheEconomistModule> modules = List.of(
                module("foundation", initializationOrder),
                module("citizens", initializationOrder),
                module("economy", initializationOrder)
        );

        ModuleLoader.initialize(modules);

        assertEquals(List.of("foundation", "citizens", "economy"), initializationOrder);
    }

    @Test
    void rejectsDuplicateIdsBeforeInitializingAnyModule() {
        List<String> initializedModules = new ArrayList<>();
        List<TheEconomistModule> modules = List.of(
                module("economy", initializedModules),
                module("economy", initializedModules)
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleLoader.initialize(modules)
        );

        assertTrue(error.getMessage().contains("economy"));
        assertTrue(initializedModules.isEmpty());
    }

    @Test
    void rejectsBlankIdsBeforeInitializingAnyModule() {
        List<String> initializedModules = new ArrayList<>();
        List<TheEconomistModule> modules = List.of(
                module("foundation", initializedModules),
                module("  ", initializedModules)
        );

        assertThrows(IllegalArgumentException.class, () -> ModuleLoader.initialize(modules));

        assertTrue(initializedModules.isEmpty());
    }

    private static TheEconomistModule module(String id, List<String> initializationOrder) {
        return new TheEconomistModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void initialize() {
                initializationOrder.add(id);
            }
        };
    }
}
