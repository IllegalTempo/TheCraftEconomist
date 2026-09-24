# Citizen Family Target Search and Memory Implementation Plan

> **For agentic workers:** Implement this plan task-by-task in the chosen execution mode. Search state and target memory are separate: each Citizen owns search progress, while each family shares remembered target locations.

**Goal:** Add a persistent, family-shared target-location memory and a walking search system that lets target-seeking Citizens search, travel, and work within a 256-block horizontal radius of home.

**Architecture:** Add an action-keyed memory store backed by `HouseholdSavedData` for family members and Citizen NBT otherwise. Add a reusable, bounded waypoint search service with block/entity target finders; work behaviors approach found targets, while emergency behaviors retain their own response movement. Integrate the service into farming, seed finding, sleep, and entity-target behaviors without bypassing their current action eligibility or ownership rules.

**Tech Stack:** Java 25, Minecraft/Fabric server APIs, Minecraft `BlockPos` and entity navigation, codec-backed `SavedData`, entity `ValueInput`/`ValueOutput` NBT.

**Spec:** `docs/superpowers/specs/2026-09-23-citizen-family-target-search-memory-design.md`

## Global Constraints

- Search and work targets stay within a 256-block horizontal radius of the Citizen's saved home anchor.
- Search advances through local waypoints; it must not scan the full radius synchronously or force-load chunks.
- Household target memory stores action-keyed `BlockPos` values only; it does not store entity UUIDs or live entity references.
- If the action finder cannot find a valid target at a remembered location, clear only that action's remembered entry and continue that Citizen's saved route.
- Each Citizen's search progress survives save and reload and remains independent from family-shared target locations.
- Emergency behaviors keep their existing response direction; a threat result must not replace fleeing navigation with an approach path.
- Preserve existing farming claims, bed reservations, action priorities, and tool, inventory, and eligibility requirements.
- Do not add or run tests or builds unless the user explicitly asks for verification.
- This workspace has no `.git` directory; do not include Git commit steps.

## Review Focus

- A remembered location outside the current home radius must be discarded before navigation or work.
- An unloaded chunk must not be synchronously loaded just to check a block or entity target.
- A stale entity location must be cleared by action-specific entity lookup without persisting or following an entity UUID.
- Two household members using one target-action key must share its location while retaining separate search cursors.
- Existing household saves without target data and Citizens without a household must load with empty or local fallback memory.

---

## File Map

- Create `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetFinder.java` for action-provided local and remembered-location checks.
- Create `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetSearchResult.java` for the search service's explicit state and optional location.
- Create `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetMemoryStore.java` to read, write, and clear family or fallback locations using stable target-action IDs.
- Create `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetSearchService.java` for local waypoints, bounded search ticks, target validation, and navigation to search points or remembered work targets.
- Modify `src/main/java/com/jedts/theeconomist/citizen/house/Household.java`, `HouseholdLedger.java`, and `HouseholdSavedData.java` to persist family-shared action-keyed locations with defaults for old saves.
- Modify `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java` to persist per-Citizen fallback locations and per-action search cursors.
- Modify `src/main/java/com/jedts/theeconomist/citizen/farm/FarmerWorkService.java`, `src/main/java/com/jedts/theeconomist/citizen/behavior/work/FarmerBehavior.java`, and `SeedFindingBehavior.java` to use shared target search and allow target work within 256 blocks.
- Modify `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/SleepBehavior.java` to use remembered, incrementally discovered bed locations while preserving bed reservations.
- Modify `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/LookAtCreatureBehavior.java`, `src/main/java/com/jedts/theeconomist/citizen/behavior/combat/CombatBehavior.java`, `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/AvoidMonsterBehavior.java`, and `PanicBehavior.java` to provide their existing action-specific entity predicates and preserve their current responses.
- Modify `src/main/java/com/jedts/theeconomist/citizen/config/DecisionScoringConfig.java` and `config/theeconomist/citizens.json` so farmer eligibility and target work radius use 256 rather than the old 32-block bound.

## Interfaces

`CitizenTargetFinder` is supplied by each action and exposes two checks:

```java
Optional<BlockPos> findAt(CitizenBehaviorContext context, BlockPos rememberedLocation);
Optional<BlockPos> findNear(CitizenBehaviorContext context, BlockPos waypoint, int localRadius);
```

`findAt` checks the remembered location using that action's current validity rules. `findNear` checks one bounded waypoint area and returns only a target location. Entity finders convert a currently eligible entity's position to `BlockPos`; no entity identity is stored.

`CitizenTargetMemoryStore` exposes:

```java
Optional<BlockPos> get(CitizenBehaviorContext context, String targetActionId);
void remember(CitizenBehaviorContext context, String targetActionId, BlockPos location);
void forget(CitizenBehaviorContext context, String targetActionId);
```

`CitizenTargetSearchService` exposes:

```java
CitizenTargetSearchResult tick(
    CitizenBehaviorContext context,
    String targetActionId,
    CitizenTargetFinder finder,
    double navigationSpeed
);
```

`CitizenTargetSearchResult` reports `FOUND`, `WALKING_TO_MEMORY`, `WALKING_SEARCH_ROUTE`, or `SEARCHING`, and carries a `BlockPos` only for `FOUND`. The service checks a family location first, clears it if `findAt` fails, then continues from that Citizen's own saved route cursor. Work behaviors navigate from `FOUND` to the target and act; emergency behaviors use the returned location as a threat and keep their existing flee movement.

## Tasks

### Task 1: Persist family target locations and Citizen search progress

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetMemoryStore.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/Household.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdLedger.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdSavedData.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`

**Interfaces:**
- Produces the `CitizenTargetMemoryStore.get/remember/forget` methods listed above.
- Produces per-Citizen integer search cursor accessors keyed by `targetActionId`.
- Household memory is resolved through the Citizen's household ID and saved home anchor; if no household record exists, the store uses Citizen-local NBT.

- [ ] **Step 1: Add household target-location data**

Add an immutable action-key-to-position map to `Household`. Include it in `HouseholdLedger.Entry` and in `HouseholdSavedData.ENTRY_CODEC` as an optional list of records containing `targetActionId`, `x`, `y`, and `z`. Decode absent records as an empty map. Copy maps on construction/export and call the existing `issuedChanged` callback after remember/forget mutations so `SavedData` becomes dirty.

- [ ] **Step 2: Add Citizen-local fallback and cursor data**

Add maps to `CitizenEntity` for fallback target locations and action cursors. Serialize them below `TheEconomistCitizen` using child records with stable action IDs and primitive coordinates/cursor values. On load, clear the maps first, accept missing fields as empty, reject blank IDs, clamp negative cursors to zero, and ignore malformed records without failing entity load.

- [ ] **Step 3: Route memory operations through one store**

Implement `get`, `remember`, and `forget` in `CitizenTargetMemoryStore`. Resolve family storage with `HouseholdSavedData.forLevel(level).ledger().get(home)` only when the Citizen has a matching household. Fall back to Citizen NBT if that entry is unavailable. On every write, reject blank action IDs, copy positions as immutable, and mark household data dirty through the existing callback.

- [ ] **Step 4: Review data migration and ownership paths**

Read both old and new codec construction paths. Confirm existing household entries with no target map decode to empty memory, one household update does not replace names or issued slots, and a family clear removes only the requested action ID.

### Task 2: Add bounded shared waypoint search

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetFinder.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetSearchResult.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetSearchService.java`
- Use: `CitizenTargetMemoryStore` from Task 1
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java` only if cursor accessors are not completed in Task 1

**Interfaces:**
- Consumes the target finder and memory store from earlier tasks.
- Produces the `CitizenTargetFinder`, `CitizenTargetSearchResult`, and service `tick` signatures in the Interfaces section.

- [ ] **Step 1: Define deterministic local waypoint progression**

Generate horizontal waypoints in an expanding square spiral with 16-block spacing. Skip any waypoint whose horizontal squared distance from home exceeds `256 * 256`. Store only the spiral cursor per Citizen and action; derive the waypoint from the cursor so the route resumes after reload without persisting a large position list.

- [ ] **Step 2: Make each search tick bounded and chunk-safe**

On each `tick`, inspect at most one local waypoint area via `findNear`; guard every world read with `level.hasChunkAt`. Never call a chunk-loading API. Increment the Citizen/action cursor after a waypoint has been searched or its path is known unreachable. When a complete spiral has covered the radius, wrap the cursor to begin a new pass.

- [ ] **Step 3: Search nearby, then try remembered locations**

First call `findNear` around the Citizen's current position using the same bounded local scan radius. Return and remember a nearby target immediately. Only if no local target exists, read the memory store. For a remembered location inside the horizontal radius, navigate toward it in local legs. Once the action finder can check that location, call `findAt`. Return `FOUND` and refresh memory on success. If the finder returns empty, call `forget` for that action and resume the saved waypoint route. Forget out-of-radius locations without navigating to them.

- [ ] **Step 4: Preserve caller-owned target response**

Return a `CitizenTargetSearchResult` rather than performing the work action. The service may navigate between search waypoints or toward a remembered work target. After `FOUND`, the caller owns the next movement: work callers approach and act, emergency callers use their current escape/look/combat policy.

- [ ] **Step 5: Review boundary and failure transitions**

Trace `FOUND`, a stale remembered location, an out-of-radius location, no target at the current waypoint, unreachable navigation, a wrapped route, and a Citizen with no household. Confirm every transition keeps the current action ID and per-Citizen cursor.

### Task 3: Integrate seeds and farming over the 256-block radius

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/farm/FarmerWorkService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/FarmerBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/SeedFindingBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/config/DecisionScoringConfig.java`
- Modify: `config/theeconomist/citizens.json`
- Use: shared target-search service and `CitizenTargetFinder` from Task 2

**Interfaces:**
- Uses target-action IDs `farmer.seed_forage`, `farmer.hoe_soil`, and `farmer.crop_plot` so family memory for one target type cannot mask another.
- Returns block locations from action-specific finders; `FarmerWorkService` remains responsible for farm inventory, claims, hoeing, planting, harvesting, and water validation.

- [ ] **Step 1: Replace the home-centered 32-block scan**

Refactor `FarmerWorkService.find` into local target-finder checks for the current scan waypoint. Keep the existing state, claim-owner, inventory, water, and ripe-crop predicates. Replace the service's fixed `RADIUS = 32` boundary with the 256-block horizontal radius from the target-search system.

- [ ] **Step 2: Keep each farmer target type's memory separate**

Use `farmer.hoe_soil` for unclaimed tillable soil that is hydrated within four horizontal blocks, and `farmer.crop_plot` for owned farmland that can be planted or harvested. The current `FarmerWorkPlanner` still chooses among valid candidates using existing ranking. On a found target, retain the same `perform` revalidation before modifying the world.

- [ ] **Step 3: Integrate seed forage search**

Use `farmer.seed_forage` with a finder that accepts the existing short-grass/fern seed-forage targets and respects the current seed capacity. When found, route to the target and keep the existing drop-to-inventory and status updates.

- [ ] **Step 4: Expand farmer eligibility to the same boundary**

Change the farmer `workRadius` default and source config entry from 32 to 256. Make `FarmerBehavior` and `SeedFindingBehavior` use that configured radius consistently for eligibility and scoring; remove the farmer service's contradictory return-home-at-32 behavior.

- [ ] **Step 5: Review continuous target movement and recovery**

Follow the no-target, moving-to-waypoint, found-target, invalid-after-path, and action-completed flows. Verify by source review that targets are still checked for water, ownership, hoe, seeds, crop age, and capacity immediately before acting.

### Task 4: Integrate sleep and block target actions

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/SleepBehavior.java`
- Use: `CitizenBedSelector.java` and `CitizenBedReservations.java` without changing their ownership semantics
- Use: shared target-search service from Task 2

**Interfaces:**
- Uses stable target-action ID `sleep.bed`.
- Bed finder returns the head-half `BlockPos`; the existing approach-position check and reservation service remain authoritative.

- [ ] **Step 1: Replace exhaustive local bed scan with incremental search**

Move the existing bed predicate into `CitizenTargetFinder.findNear`, retaining head-half selection, occupancy, approach clearance, and reachability requirements. Search through the same 256-block home-centered waypoint route without enumerating the full cube around home.

- [ ] **Step 2: Reuse saved family bed location first**

Use `sleep.bed` to revisit a remembered bed. If the bed is gone, occupied, unreachable, or has no valid approach, release its reservation, clear only `sleep.bed`, and resume the Citizen's saved route. On success, reserve the bed before pathing as today.

- [ ] **Step 3: Preserve sleep interruption cleanup**

Keep `SleepBehavior.stop`, wake handling, invalid-bed handling, retry timing, and reservation release. Ensure search cancellation on preemption does not leave navigation or a bed reservation owned by the sleeping behavior.

### Task 5: Integrate entity targets, including emergency behaviors

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/LookAtCreatureBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/combat/CombatBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/AvoidMonsterBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/PanicBehavior.java`
- Use: shared target-search service from Task 2

**Interfaces:**
- Use stable IDs `look_at_creature.entity`, `combat.challenger`, `avoid_monster.threat`, and `panic.attacker`.
- Every finder returns the entity's current `BlockPos` only. Finder eligibility must match that behavior's existing target class and incident conditions.

- [ ] **Step 1: Add location-only memory to creature watching**

Adapt `LookAtCreatureBehavior`'s existing `LivingEntity` query (`Mob` or `Player`, excluding the Citizen and requiring alive) into a finder. On a found target, travel to its returned location before the existing look action. Do not save the entity instance in family memory.

- [ ] **Step 2: Reuse location memory for challenge combat**

Keep `LandConflictService` as the authority that starts a combat incident and sets an action-eligible challenger. Store only the challenger's block location under `combat.challenger`. When resuming, re-find only an entity that satisfies the current combat incident/target rules at that location; if none exists, clear that memory and do not attack an unrelated entity. Keep combat scoring, range, and cooldown behavior unchanged.

- [ ] **Step 3: Integrate emergency threat memories without reversing movement**

Adapt `AvoidMonsterBehavior` to its existing alive-`Monster` finder and `PanicBehavior` to its current last-hurt-by-mob finder. A confirmed threat location may be saved or rechecked through the shared service, but after confirmation each behavior must continue calling its existing away-position logic (`DefaultRandomPos.getPosAway`) and speed. Never use the service's approach-to-work-target navigation for the emergency response.

- [ ] **Step 4: Keep preemption and cancellation safe**

When an emergency starts or stops, it may suspend a lower-priority search but must not erase that action's family memory or cursor. When the emergency target is absent at its remembered location, clear only that emergency action key and continue its own search state while that emergency remains eligible.

### Task 6: Review the integrated target-search flow

**Files:**
- Review: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/`
- Review: all consumer files listed in Tasks 3–5
- Review: `HouseholdSavedData.java`, `Household.java`, and `CitizenEntity.java`

**Interfaces:**
- No new public interface; this pass checks that all consumers use the APIs defined in Tasks 1–2.

- [ ] **Step 1: Review each spec requirement against its owner**

Trace family sharing and legacy-save defaults to Task 1; radius, local waypoints, no force-load, and saved cursors to Task 2; 256-block farming and seeds to Task 3; beds/reservations to Task 4; entity location-only memory and emergency movement to Task 5.

- [ ] **Step 2: Review existing behavior policies**

Confirm the target system does not change action scoring, family claims, bed reservations, emergency flee direction, combat challenge eligibility, or tool/inventory constraints. No tests or build commands are run unless the user asks for verification.

## Execution Notes

The data model, search service, and behavior integrations depend on the exact interfaces established in earlier tasks, so task-by-task native execution is the recommended approach. The workspace has no Git metadata, so isolated Git worktrees and commit tasks are unavailable. The previously reported offline dependency limitation may also prevent a Gradle build if one is later requested.
