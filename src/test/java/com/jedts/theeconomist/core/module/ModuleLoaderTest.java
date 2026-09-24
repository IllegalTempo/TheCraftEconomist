package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import com.jedts.theeconomist.core.module.ModModules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModuleLoaderTest {
    @Test
    void projectModulesKeepTheirCurrentStartupOrder() {
        assertEquals(List.of("currency", "citizen", "blueprint", "trade"),
                ModModules.all().stream().map(TheEconomistModule::id).toList());
    }

    @Test
    void citizenModuleDeclaresItsCurrencyPrerequisite() {
        TheEconomistModule citizen = ModModules.all().stream()
                .filter(module -> module.id().equals("citizen"))
                .findFirst()
                .orElseThrow();

        assertEquals(Set.of("currency"), citizen.dependencies());
    }

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

    @Test
    void initializesDependenciesBeforeDependentsEvenWhenDeclaredLater() {
        List<String> initializationOrder = new ArrayList<>();
        List<TheEconomistModule> modules = List.of(
                module("feature", Set.of("foundation"), initializationOrder),
                module("foundation", Set.of(), initializationOrder)
        );

        ModuleLoader.initialize(modules);

        assertEquals(List.of("foundation", "feature"), initializationOrder);
    }

    @Test
    void rejectsMissingDependencyBeforeInitializingAnyModule() {
        List<String> initializedModules = new ArrayList<>();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModuleLoader.initialize(List.of(
                        module("feature", Set.of("missing"), initializedModules))));

        assertTrue(error.getMessage().contains("missing"));
        assertTrue(initializedModules.isEmpty());
    }

    @Test
    void rejectsDependencyCyclesBeforeInitializingAnyModule() {
        List<String> initializedModules = new ArrayList<>();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModuleLoader.initialize(List.of(
                        module("alpha", Set.of("beta"), initializedModules),
                        module("beta", Set.of("alpha"), initializedModules))));

        assertTrue(error.getMessage().contains("alpha"));
        assertTrue(error.getMessage().contains("beta"));
        assertTrue(initializedModules.isEmpty());
    }

    @Test
    void cycleDiagnosticDoesNotIncludeModulesBlockedDownstream() {
        List<String> initializedModules = new ArrayList<>();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModuleLoader.initialize(List.of(
                        module("alpha", Set.of("beta"), initializedModules),
                        module("beta", Set.of("alpha"), initializedModules),
                        module("dependent", Set.of("alpha"), initializedModules))));

        assertTrue(error.getMessage().contains("alpha"));
        assertTrue(error.getMessage().contains("beta"));
        assertFalse(error.getMessage().contains("dependent"));
        assertTrue(initializedModules.isEmpty());
    }

    @Test
    void rejectsBlankDependencyIdsBeforeInitializingAnyModule() {
        List<String> initializedModules = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> ModuleLoader.initialize(List.of(
                module("feature", Set.of(" "), initializedModules))));

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

    private static TheEconomistModule module(String id, Set<String> dependencies,
                                             List<String> initializationOrder) {
        return new TheEconomistModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> dependencies() {
                return dependencies;
            }

            @Override
            public void initialize() {
                initializationOrder.add(id);
            }
        };
    }
}
