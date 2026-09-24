package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Validates and initializes feature modules in their declared dependency order.
 */
public final class ModuleLoader {
    private ModuleLoader() {
    }

    public static void initialize(List<? extends TheEconomistModule> modules) {
        Objects.requireNonNull(modules, "modules");
        orderByDependencies(modules).forEach(TheEconomistModule::initialize);
    }

    private static List<TheEconomistModule> orderByDependencies(
            List<? extends TheEconomistModule> modules) {
        Map<String, TheEconomistModule> modulesById = new LinkedHashMap<>();
        Map<String, Integer> declarationOrder = new HashMap<>();
        Map<String, Set<String>> dependenciesById = new LinkedHashMap<>();

        for (int index = 0; index < modules.size(); index++) {
            TheEconomistModule module = modules.get(index);
            Objects.requireNonNull(module, "modules cannot contain null entries");
            String moduleId = module.id();

            if (moduleId == null || moduleId.isBlank()) {
                throw new IllegalArgumentException("Module IDs must not be blank");
            }

            if (modulesById.putIfAbsent(moduleId, module) != null) {
                throw new IllegalArgumentException("Duplicate module ID: " + moduleId);
            }
            declarationOrder.put(moduleId, index);

            Set<String> dependencies = module.dependencies();
            if (dependencies == null) {
                throw new IllegalArgumentException("Module dependencies must not be null: " + moduleId);
            }
            for (String dependency : dependencies) {
                if (dependency == null || dependency.isBlank()) {
                    throw new IllegalArgumentException("Module dependency IDs must not be blank: " + moduleId);
                }
            }
            dependenciesById.put(moduleId, Set.copyOf(dependencies));
        }

        Map<String, List<String>> dependentsById = new HashMap<>();
        Map<String, Integer> remainingDependencies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : dependenciesById.entrySet()) {
            String moduleId = entry.getKey();
            Set<String> dependencies = entry.getValue();
            remainingDependencies.put(moduleId, dependencies.size());
            for (String dependency : dependencies) {
                if (!modulesById.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Module '" + moduleId + "' depends on missing module '" + dependency + "'");
                }
                dependentsById.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(moduleId);
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(
                Comparator.comparingInt(declarationOrder::get));
        remainingDependencies.forEach((id, count) -> {
            if (count == 0) ready.add(id);
        });

        List<TheEconomistModule> ordered = new ArrayList<>(modules.size());
        while (!ready.isEmpty()) {
            String moduleId = ready.remove();
            ordered.add(modulesById.get(moduleId));
            for (String dependent : dependentsById.getOrDefault(moduleId, List.of())) {
                int remaining = remainingDependencies.compute(dependent, (id, count) -> count - 1);
                if (remaining == 0) ready.add(dependent);
            }
        }

        if (ordered.size() != modules.size()) {
            Set<String> unresolvedModules = remainingDependencies.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> cycle = findCycle(unresolvedModules, dependenciesById, declarationOrder);
            throw new IllegalArgumentException("Cycle in module dependencies: " + cycle);
        }

        return List.copyOf(ordered);
    }

    private static List<String> findCycle(
            Set<String> unresolvedModules,
            Map<String, Set<String>> dependenciesById,
            Map<String, Integer> declarationOrder) {
        Map<String, Integer> states = new HashMap<>();
        List<String> path = new ArrayList<>();
        List<String> roots = unresolvedModules.stream()
                .sorted(Comparator.comparingInt(declarationOrder::get))
                .toList();

        for (String moduleId : roots) {
            List<String> cycle = findCycle(moduleId, unresolvedModules, dependenciesById,
                    declarationOrder, states, path);
            if (cycle != null) return cycle;
        }
        throw new IllegalStateException("Unresolved modules did not contain a dependency cycle");
    }

    private static List<String> findCycle(
            String moduleId,
            Set<String> unresolvedModules,
            Map<String, Set<String>> dependenciesById,
            Map<String, Integer> declarationOrder,
            Map<String, Integer> states,
            List<String> path) {
        states.put(moduleId, 1);
        path.add(moduleId);
        List<String> dependencies = dependenciesById.get(moduleId).stream()
                .filter(unresolvedModules::contains)
                .sorted(Comparator.comparingInt(declarationOrder::get))
                .toList();

        for (String dependency : dependencies) {
            int state = states.getOrDefault(dependency, 0);
            if (state == 1) {
                int cycleStart = path.indexOf(dependency);
                List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(dependency);
                return cycle;
            }
            if (state == 0) {
                List<String> cycle = findCycle(dependency, unresolvedModules, dependenciesById,
                        declarationOrder, states, path);
                if (cycle != null) return cycle;
            }
        }

        path.remove(path.size() - 1);
        states.put(moduleId, 2);
        return null;
    }
}
