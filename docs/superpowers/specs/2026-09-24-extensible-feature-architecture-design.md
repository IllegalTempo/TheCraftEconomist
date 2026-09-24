# Extensible Feature Architecture Design

## Purpose

Establish a repeatable way to add gameplay features to The Economist without scattering registration, lifecycle, or feature-specific rules across unrelated classes. The design builds on the existing module loader and citizen behavior registry, keeps the mod focused on its current Fabric architecture, and migrates incrementally without changing gameplay or saved data as an incidental effect.

## Current context

- `ModModules` owns a fixed ordered list of `CurrencyModule`, `CitizenModule`, `BlueprintModule`, and `TradeModule`.
- `ModuleLoader` validates null modules and duplicate IDs, then initializes modules in list order. Modules do not currently declare or validate dependencies or lifecycle stages.
- `CitizenBehaviorRegistry` is an existing extension seam, but its defaults are assembled directly in that registry.
- `CitizenModule` currently coordinates many registrations and lifecycle hooks.
- `CitizenEntity` is a large integration point (526 lines at review time) for citizen state and runtime behavior.
- The project has broad domain tests and uses JUnit 5 with Fabric/Minecraft APIs.

## Goals

1. Make module ownership, dependencies, and initialization order explicit and validated.
2. Provide clear registration seams for features that are intended to be extended, beginning with citizen behaviors and reusable citizen actions.
3. Keep feature rules in focused components and limit entity classes and module entry points to integration and delegation.
4. Define a consistent contributor workflow for adding a feature, including registration, configuration/persistence considerations, and focused tests.
5. Preserve current behavior and existing world/config compatibility during the refactor.

## Non-goals

- Loading arbitrary third-party plugins or discovering modules from the classpath.
- Designing a universal event bus or dependency injection container.
- Replacing Fabric registration APIs or moving all gameplay data to data packs.
- Rewriting all existing systems at once or changing gameplay balance.
- Splitting classes solely to satisfy line-count limits.

## Design

### 1. Explicit module graph and lifecycle

Retain the project-owned module inventory as the trusted source of available modules. Extend the module contract so each module declares a stable ID and its module dependencies. The loader validates unique IDs, missing dependencies, and dependency cycles, then initializes modules in deterministic topological order. Registration remains one-time and predictable from the mod initializer.

The lifecycle contract should distinguish registration/setup from runtime server start and stop work only where current features need those phases. Avoid adding empty lifecycle methods or runtime abstractions without a concrete consumer. Existing server callbacks should be owned by the module or a service it initializes, with cleanup paired to setup.

### 2. Owned extension registries

For extension seams that already exist or have multiple consumers, use small typed registries with explicit IDs, deterministic ordering where order affects behavior, and duplicate validation. Start by making behavior registration an owned part of citizen feature initialization instead of a standalone hard-coded defaults list. Reusable action/resource-provider registration should follow only where there are multiple independent implementations or a demonstrated upcoming feature need.

Registries are internal extension APIs for this mod's modules. They are not a promise of a stable third-party binary plugin API. Avoid mutable global registries after initialization; collect registrations during module setup and expose an immutable view to runtime consumers.

### 3. Feature boundaries around the citizen entity

Treat `CitizenEntity` as the Minecraft entity adapter: own entity lifecycle, Minecraft-facing state synchronization, and delegation to focused citizen services. Move a responsibility only when its inputs and outputs can be named and tested at a boundary—for example, persistence conversion, decision/behavior coordination, or inventory interaction. Keep domain services independent of entity implementation where practical, while passing explicit context for unavoidable world/entity operations.

Do not attempt a one-shot decomposition. Select one cohesive responsibility, preserve serialization keys and runtime behavior, test it, then repeat based on remaining coupling. This reduces risk to citizen NBT and save compatibility.

### 4. Feature addition workflow

Document a feature checklist and examples: declare module ownership/dependencies; register through the owning module; put behavior in the appropriate domain service; define stable IDs and config/persistence defaults when needed; add focused tests for rules and module/registry validation; verify client/server registration boundaries. Add only checks that catch concrete architecture regressions, such as dependency cycles, duplicate IDs, and invalid registrations.

## Alternatives considered

1. **Keep lists and conventions, document only.** Lowest initial cost, but ordering and ownership mistakes remain runtime surprises.
2. **Introduce a general plugin framework.** Offers broad extensibility but adds discovery, compatibility, and lifecycle machinery without a current third-party plugin requirement.
3. **Strengthen current module and registry seams (recommended).** Improves feature addition within the project's current architecture, limits scope, and supports incremental migration.

## Migration and compatibility

Perform the work in independently reviewable stages: characterize module initialization and existing behavior; introduce and validate module metadata/order; move behavior registration behind module ownership; extract one cohesive citizen entity responsibility; add further seams only where a real feature requires them; document the contributor workflow. Preserve current module IDs unless there is a compelling migration reason. Keep citizen NBT field names, config format, networking payloads, and gameplay decisions unchanged unless a separate approved change explicitly requires otherwise.

## Verification

- Unit tests cover duplicate/missing module IDs, missing dependencies, dependency cycles, and deterministic initialization order.
- Registry tests cover duplicate IDs, stable ordering where relevant, immutable post-setup access, and factory failures.
- Existing citizen behavior and entity persistence tests continue to pass during responsibility extraction.
- A feature can be added by implementing its owning module/registration contract without editing unrelated feature internals.
- The full unit test suite and Fabric GameTest/build checks are run at plan-defined checkpoints.

## Risks and mitigations

- **Overengineering registries:** introduce them only for existing multi-implementation seams; avoid generic event buses and reflective discovery.
- **Order changes alter behavior:** preserve declared current order as a tie-breaker for otherwise independent modules and lock it with tests.
- **Entity extraction changes saves:** retain serialization keys and add compatibility coverage before moving persistence logic.
- **Broad migration blocks feature work:** sequence small deliverables and stop after the core extension method is documented and demonstrated.
