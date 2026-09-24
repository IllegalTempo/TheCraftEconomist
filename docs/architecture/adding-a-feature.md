# Adding a Feature

This guide is the project workflow for adding a feature without spreading its setup and rules across unrelated systems. Features are project-owned Fabric modules; this is not a third-party plugin API.

## 1. Choose the feature owner and boundaries

Start in the package that owns the game concept. Keep registration and lifecycle hookup in that feature's `TheEconomistModule`; keep rules in focused services or domain objects; use Minecraft entities, levels, inventories, and networking at the boundary where the feature needs them. Do not move a rule into a central class just because that class is easy to find.

Before adding an abstraction, look for a second real consumer. A typed registry is useful when several independently added implementations share an extension point. Do not create an action/provider registry, event bus, or generic service container for a single implementation.

## 2. Add a feature module

Create a module in the owning package and give it a permanent ID. List only modules whose registrations or public feature APIs it needs during initialization:

```java
import java.util.Set;

public final class SettlementModule implements TheEconomistModule {
    @Override
    public String id() {
        return "settlement";
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("citizen", "currency");
    }

    @Override
    public void initialize() {
        SettlementBlocks.register();
        SettlementNetworking.register();
    }
}
```

Add the module to `ModModules.all()`. `ModuleLoader` validates every module ID and dependency before initializing any module. Missing dependencies and cycles stop startup with an error. Declared dependencies determine the order; the `ModModules` list order breaks ties between modules that are ready together. Keep initialization registration-only. Put server start/stop listeners in the owning module or its service, and pair runtime setup with cleanup where needed.

When a feature does not depend on another module, leave `dependencies()` at its default empty set. Do not declare dependencies merely to preserve a preferred sequence.

## 3. Add a citizen behavior

Implement `CitizenBehavior` with a stable behavior ID, evaluation, eligibility, tick, status, and cleanup for any active navigation, reservation, target, or other runtime state. Behaviors use a new instance per Citizen; do not put shared mutable behavior state in static fields.

Register the behavior factory in `CitizenModule.createBehaviorRegistry()`:

```java
.register("market_work", MarketWorkBehavior::new)
```

Keep the registered ID equal to `MarketWorkBehavior.id()`. Registration order is deterministic and matters only as a tie-breaker. The builder records immutable IDs and factories without constructing behaviors during module setup. When a Citizen controller is created, the factory runs and the registry checks the resulting behavior ID. This avoids early Minecraft item/world initialization while still reporting factory and ID errors before the controller is used.

Use `CitizenBehaviorContext` for live server state. Put pure scoring or eligibility rules in small domain methods when they can be tested without an entity or world. Add focused tests for those rules and registry validation; add a Fabric GameTest when the feature changes visible world behavior.

## 4. Keep client, server, and saved data compatible

- Register server-authoritative content and common networking payloads from the owning module. Keep client hooks, screens, and renderers in client initialization.
- Keep persistent IDs stable after release. For new saved fields, define defaults for old saves and preserve existing field names unless a separate migration is intended.
- Treat config additions the same way: choose a default, keep partial existing files valid, and test load/encode behavior.
- Avoid retaining live world, entity, or registry references in saved data. Rebuild transient runtime objects from stable IDs and saved values.

## 5. Verify the extension path

Add module tests for missing dependency, dependency cycle, duplicate IDs, and deterministic initialization order when the dependency graph changes. Add registry tests for duplicate IDs, invalid factories, and stable order when a registry changes. Keep behavior/domain tests near their feature packages.

On Windows, run focused tests with `.\gradlew.bat test --tests <test-class>` and the complete check with `.\gradlew.bat build`. On Linux or macOS, use `./gradlew` with the same arguments. Review client/server initialization, existing save keys, config defaults, and networking payload ownership before finishing.
