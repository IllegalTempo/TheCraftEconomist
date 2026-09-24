# Extensible Feature Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make future gameplay features easier to add by defining explicit module dependencies, owned extension registries, focused citizen state boundaries, and a repeatable contributor workflow.

**Architecture:** Strengthen the existing `TheEconomistModule`/`ModuleLoader` startup path with declared dependencies and deterministic topological ordering. Keep behavior registration owned by the citizen feature through a validated registry builder, extract target search memory from `CitizenEntity` into a focused component, and document how a new feature uses these seams. Do not add runtime plugin discovery, a generic event bus, or broad dependency injection.

**Tech Stack:** Java 25, Fabric Loader/API, Minecraft serialization APIs, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-24-extensible-feature-architecture-design.md`

## Global Constraints

- Preserve current gameplay decisions and current module IDs.
- Keep citizen NBT field names, config format, and networking payloads compatible.
- Keep registration project-owned, deterministic, and one-time.
- Avoid reflective discovery, a universal event bus, and registries without multiple actual contributions.
- Do not force-load chunks or move client-only registration to common/server initialization.

## Review Focus

- Missing module dependency or dependency cycle: fail before any module initializes and identify the offending ID/dependency.
- Independent modules with multiple valid orderings: retain deterministic ordering with the current declaration order as tie-breaker.
- Duplicate behavior ID or factory failure: reject duplicate registrations during setup and report factory failures before returning a controller.
- Old citizen save without optional search-memory entries: load with empty memory and preserve all unrelated citizen state.
- Citizen removed or changing dimension while retaining search memory: do not return a remembered target from the wrong dimension.

---

### Task 1: Characterize current extension and persistence contracts

**Files:**
- Modify: `src/test/java/com/jedts/theeconomist/core/module/ModuleLoaderTest.java`
- Inspect: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`

**Interfaces:**
- Record the current startup order as currency, citizen, blueprint, trade before changing the loader.
- Record target memory expectations from `CitizenEntity.localTargetLocation`, `rememberLocalTarget`, `forgetLocalTarget`, cursor accessors, and save/load methods for Task 4.

- [x] **Step 1: Add a test asserting current project module IDs and startup sequence**

Use `ModModules.all()` to assert IDs are `currency`, `citizen`, `blueprint`, and `trade` in the existing declared order. Keep the test independent of Fabric initialization by inspecting metadata only.

- [x] **Step 2: Inspect the entity persistence block and map target-memory keys**

Read `CitizenEntity.addAdditionalSaveData` and `readAdditionalSaveData`. Record the existing `TheEconomistCitizen` child key and its `TargetLocations`, `TargetSearchCursors`, and `TargetSearchDimensions` list keys for the extraction. Do not rename or normalize persisted keys.

- [x] **Step 3: Record untouched config and networking contracts**

Note the current citizen config path and networking payload registrations in the review checklist. These are compatibility comparison points, not planned changes.

- [x] **Step 4: Run the focused characterization tests**

Run: `.\gradlew.bat test --tests com.jedts.theeconomist.core.module.ModuleLoaderTest`

Expected: PASS before production refactoring; failures should reveal an incorrect assumption about current behavior and be resolved in the tests before continuing.

### Task 2: Declare and validate module dependencies

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/api/module/TheEconomistModule.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModuleLoader.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Modify: `src/test/java/com/jedts/theeconomist/core/module/ModuleLoaderTest.java`

**Interfaces:**
- Add `default Set<String> dependencies()` to `TheEconomistModule`, returning an empty set for modules without prerequisites.
- `ModuleLoader.initialize(List<? extends TheEconomistModule>)` validates all metadata before initialization, then initializes a deterministic topological ordering.
- Declare the verified `citizen` → `currency` dependency because citizen coin denomination setup reads the registered `CrownItems`; leave blueprint and trade dependency-free unless load-time registry inspection shows otherwise.

- [x] **Step 1: Add failing tests for a valid dependency overriding declaration order**

Create modules `foundation` and `feature`, pass them in reverse order, declare `feature` depends on `foundation`, and assert initialization runs foundation first.

- [x] **Step 2: Add failing tests for missing dependency and cycle rejection**

Assert both invalid graphs throw `IllegalArgumentException` naming the missing dependency or cycle and initialize none of the modules.

- [x] **Step 3: Add failing tests for stable ordering and invalid dependency IDs**

For independent modules assert their input declaration order is retained.

- [x] **Step 4: Implement dependency metadata and deterministic topological sorting**

Keep `id()` and `initialize()` intact. Validate null modules, blank/duplicate module IDs, blank/missing dependency IDs, and cycles before invoking any initializer. Resolve simultaneously-ready modules by their original declaration index.

- [x] **Step 5: Declare the real project module prerequisite**

Set `CitizenModule.dependencies()` to `Set.of("currency")`. Update `ModModules` documentation to say dependencies determine order and list order breaks ties. Keep `CurrencyModule`, `BlueprintModule`, and `TradeModule` dependency-free because their startup registration does not require another module's registered objects.

- [x] **Step 6: Run module loader and project module tests**

Run: `.\gradlew.bat test --tests com.jedts.theeconomist.core.module.ModuleLoaderTest`

Expected: PASS, with the project module ID/order characterization test unchanged and all invalid graphs rejected before initialization.

### Task 3: Make citizen behavior registration explicit and validated

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenRuntime.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorController.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/CitizenModuleTest.java`
- Modify: `src/test/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistryTest.java`

**Interfaces:**
- `CitizenBehaviorRegistry.Builder.register(String id, Supplier<CitizenBehavior> factory)` records a behavior in deterministic registration order.
- `CitizenBehaviorRegistry.Builder.build()` validates registered IDs/factories for nulls and duplicates, then returns an immutable registry without invoking behavior constructors during module setup.
- `CitizenBehaviorRegistry.createController()` creates fresh behavior instances from registered factories; no behavior instance is shared across Citizens.
- `createController()` validates each factory-produced behavior ID before returning the controller, after Minecraft bootstrap has completed.
- `CitizenModule` owns the default behavior contributions and publishes the completed registry through the existing citizen runtime initialization path.

- [x] **Step 1: Add registry tests for current default order and fresh instances**

Assert the existing default behavior IDs retain their current order. Also register two test behaviors and assert `behaviorIds()` preserves their order and separate controllers receive separate behavior instances.

- [x] **Step 2: Add registry tests for duplicate IDs, mismatched IDs, null factories, and factory exceptions**

Assert invalid registration metadata is rejected during setup, while behavior ID mismatches and constructor failures are reported with their registered ID when controller creation reaches the factory.

- [x] **Step 3: Implement the builder and immutable registry representation**

Keep the current default behavior order exactly as it is. The builder validates a factory's created behavior ID against the registered ID and stores factories, not behavior instances.

- [x] **Step 4: Move default contributions to citizen module ownership**

Have `CitizenModule` register the existing behaviors in their current order and initialize one immutable registry. Update `CitizenRuntime` and entity/controller construction to consume that registry instead of calling a global defaults factory.

- [x] **Step 5: Run behavior registry and controller tests**

Run: `.\gradlew.bat test --tests com.jedts.theeconomist.citizen.behavior.CitizenBehaviorRegistryTest --tests com.jedts.theeconomist.citizen.behavior.CitizenBehaviorControllerTest`

Expected: PASS with identical default order and no behavior instances shared between Citizens.

### Task 4: Extract target search memory from CitizenEntity

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenTargetMemory.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/test/java/com/jedts/theeconomist/citizen/entity/CitizenTargetMemoryTest.java`

**Interfaces:**
- `CitizenTargetMemory` owns local target positions/dimensions, search cursors, and cursor dimensions. Its rule-facing methods accept a dimension string explicitly so behavior can be tested without constructing a Minecraft world.
- Preserve the existing `CitizenEntity` methods as delegating accessors so callers do not change during this extraction.
- Preserve `localTargetLocation(String id)`, `rememberLocalTarget(String id, BlockPos pos)`, `forgetLocalTarget(String id)`, `targetSearchCursor(String id)`, `targetSearchCursor(String id, int value)`, `targetSearchDimension(String id)`, and `targetSearchDimension(String id, String dimension)` on `CitizenEntity` as delegating accessors.
- Persistence helpers read and write the same existing NBT keys and treat absent optional entries as empty/default values.

- [x] **Step 1: Implement target memory rule tests**

Test target round-trip, target forgetting, cursor clamping, dimension-scoped lookup, and empty defaults. Add a serialization contract test that confirms writes retain the existing `TargetLocations`, `TargetSearchCursors`, and `TargetSearchDimensions` keys, and reading a root with those lists absent yields empty/default memory. Pin rule behavior to Task 1 characterization.

- [x] **Step 2: Add the target memory component without changing CitizenEntity callers**

Move the four maps and their access/update logic into the component. Keep `BlockPos` immutable when stored and validate IDs as the current entity methods do.

- [x] **Step 3: Delegate entity accessors to the component**

Retain the current public method signatures and delegate reads/writes. Do not change behavior call sites in this task.

- [x] **Step 4: Delegate persistence while preserving old save keys**

Move only target-memory encode/decode operations from entity save/load methods. Keep all keys, dimension string formats, and missing-field defaults unchanged; do not move other entity persistence in this task.

- [x] **Step 5: Run target memory and citizen persistence tests**

Run: `.\gradlew.bat test --tests com.jedts.theeconomist.citizen.entity.CitizenTargetMemoryTest --tests com.jedts.theeconomist.citizen.entity.CitizenIdentityNbtTest --tests com.jedts.theeconomist.citizen.entity.CitizenStatsNbtTest --tests com.jedts.theeconomist.citizen.entity.CitizenJobNbtTest --tests com.jedts.theeconomist.citizen.entity.CitizenSkillsNbtTest`

Expected: PASS; existing citizen data remains readable and target memory behavior remains unchanged.

### Task 5: Document the feature extension workflow

**Files:**
- Create: `docs/architecture/adding-a-feature.md`
- Modify: `README.md`
- Modify: `src/main/java/com/jedts/theeconomist/api/module/TheEconomistModule.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`

**Interfaces:**
- The guide is the canonical contributor method for adding a project-owned feature module and extending citizen behaviors.
- Module and registry API documentation points contributors to the guide and states validation/order guarantees.

- [x] **Step 1: Write a module addition walkthrough using a small hypothetical feature**

Show how to implement a module ID, declare only real dependencies, add the module to `ModModules`, and test the declared dependency ordering. Do not add a production example module solely for documentation.

- [x] **Step 2: Write a citizen behavior addition walkthrough**

Show how to implement `CitizenBehavior`, register its stable ID/factory through citizen module ownership, keep Minecraft world operations in the supplied context, and add registry plus behavior rule tests.

- [x] **Step 3: Document client/server registration and persistence checks**

Include checks for client-only classes, networking payload ownership, stable IDs, optional config defaults, and backward-compatible entity/world data. Explain that actions/providers receive their own registry only when a second real contribution requires it.

- [x] **Step 4: Link the contributor guide from README and API/module comments**

Keep the README link brief and place detailed steps in the architecture guide.

### Task 6: Verify extension guarantees across the project

**Files:**
- Modify only tests if a contract regression is found in Tasks 2–4.
- Review: `docs/architecture/adding-a-feature.md`

- [x] **Step 1: Run the full unit test suite**

Run: `.\gradlew.bat test`

Expected: PASS with the new module, registry, and target-memory tests included.

- [x] **Step 2: Run Fabric compilation and packaging**

Run: `.\gradlew.bat build`

Expected: PASS for common/client compilation and packaged resources; resolve only regressions caused by this refactor.

- [x] **Step 3: Review initialization and save compatibility**

Compare module initialization order, default behavior order, citizen NBT keys, config keys, and networking payload types with their Task 1 baseline. Record any intentional changes as separate scope instead of silently including them.

- [x] **Step 4: Validate the documented extension path against one current feature**

Walk through the guide using `TradeModule` for module ownership/dependency steps and `FarmerBehavior` for behavior registration steps. Confirm the guide identifies the correct registration owner and tests without requiring unrelated module edits.

