# Citizen Household Resource Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let household Citizens find missing materials, share them in a home chest, craft requested items through reusable actions, and place one composter by the farm.

**Architecture:** Add a persistent household chest location and server-side storage/claim services. Compose reusable `FindItemAction`, `CraftItemAction`, and `PlaceItemAction` instances, with gatherable resource providers using the existing family target search. Add a composter behavior that requests one household composter and reports the active action's status.

**Tech Stack:** Java, Minecraft server APIs, Fabric lifecycle hooks, codec-backed `SavedData`, existing Citizen behavior and target search services.

**Spec:** `docs/superpowers/specs/2026-09-24-citizen-household-resource-actions-design.md`

## Global Constraints

- Crafting materials are shared by the household through a physical chest in its home.
- Use registered Minecraft crafting recipes for craftable items and support intermediate crafting requirements, including a crafting table.
- Use the existing household target memory and incremental search behavior to find world resources.
- Search within the existing 256-block home radius and never force-load chunks.
- Before harvesting, transferring, crafting, or placing, revalidate the block/container and capacity at the target.
- Search and crafting work is incremental and bounded per tick.
- Personal Citizens without a household use their own saved inventory as the storage fallback.

## Review Focus

- Missing, replaced, inaccessible, or full household chest: action reports a useful status and preserves carried items.
- Recipe cycles, absent recipes, or missing resource providers: planner terminates and does not consume ingredients.
- Stale remembered log location or unloaded chunk: memory is cleared or search resumes without force-loading.
- Citizen inventory fills or Citizen unloads during transfer: materials remain accounted for and another resident can continue.
- Two household members request a composter together: one shared claim prevents duplicate crafting and placement.

---

### Task 1: Persist and establish household storage

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/CitizenHousePiece.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HousePopulationService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/Household.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdLedger.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdSavedData.java`

**Interfaces:**
- `CitizenHousePiece` exposes the stable world position of its designated household chest, transformed with the house rotation. Select the local position after inspecting `src/main/resources/data/theeconomist/structure/citizen_house.nbt`.
- `Household.storagePosition(): Optional<BlockPos>` exposes the saved position.
- `Household.assignStoragePosition(BlockPos)` sets the location and marks household saved data dirty.
- `HousePopulationService` initializes storage idempotently when a loaded house is populated; it repairs legacy household records with no position from the matching structure piece.

- [ ] **Step 1: Inspect the house template and pick its storage site**

Read the template's block positions and identify a clear interior floor position adjacent to a wall. Add a local storage-position constant and a `storage()` transform method to `CitizenHousePiece`, parallel to `entrance()`.

- [ ] **Step 2: Persist the household chest coordinate**

Extend `Household`, `HouseholdLedger.Entry`, and the `ENTRY_CODEC` in `HouseholdSavedData` with an optional storage X/Y/Z value. Missing values decode as absent so existing saves remain valid. `assignStoragePosition` must call the existing changed callback.

- [ ] **Step 3: Initialize and repair household storage from loaded structure metadata**

Update the pending-house record in `HousePopulationService` to carry both the entrance anchor and the transformed chest position from `CitizenHousePiece`. Before issuing residents, verify the position is loaded. If it contains a chest, assign it; if it is air at the designated site, place a chest there; if obstructed or otherwise unusable, retain no false assignment and report a storage-unavailable state for later retry. For an existing household with an absent storage coordinate, use the loaded structure piece to repair it.

- [ ] **Step 4: Review persistence and retry paths**

Walk through new household creation, existing household decode with no storage field, chunk unload before initialization, and repeated `populateLoadedHouse` calls. Confirm initialization is idempotent and does not replace non-air player blocks.

### Task 2: Add household chest access and request claims

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdStorage.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdWorkClaims.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/Household.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdSavedData.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`

**Interfaces:**
- `HouseholdStorage.resolve(CitizenBehaviorContext): Optional<Container>` resolves only the assigned, currently loaded household chest and validates that the block still has a usable container.
- `HouseholdStorage.count(Container, Item): int`, `insert(Container, ItemStack): ItemStack`, and `extract(Container, Item, int): ItemStack` perform bounded item transfers while preserving stack components and return untransferred remainders.
- `HouseholdWorkClaims.tryClaim(UUID householdId, String requestId, UUID citizenId, long gameTime): boolean`, `renew(...)`, and `release(...)` coordinate one active worker per household request. Claims expire after a bounded lease if a resident unloads or the server stops.

- [ ] **Step 1: Implement chest resolution and item transfer helpers**

Resolve the assigned chest position through the current server level only when `hasChunkAt(position)` is true. Reject a missing, replaced, non-container, or inaccessible block. Implement count/insert/extract over container slots; never discard remainder stacks.

- [ ] **Step 2: Add expiring household request claims**

Keep claims in a server-side runtime map keyed by household UUID and stable request ID. Store claimant UUID and expiry tick. Reject a different live claimant, allow the owner to renew, allow reclaim after expiry, and make release idempotent. Do not persist active claims.

- [ ] **Step 3: Define action lifecycle release points**

Make reusable actions release a claim on completion, cancellation, and error. Renew claims while work progresses. Let lease expiry recover after entity unload or process shutdown; no separate entity-removal hook should be needed for correctness.

- [ ] **Step 4: Review item accounting cases**

Trace a partial insertion, partial extraction, chest replacement, unloaded chest chunk, and expired claimant. For every branch, account for every stack in either the chest or the Citizen inventory.

### Task 3: Implement reusable find-item actions and log gathering

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/CitizenItemAction.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/CitizenItemActionResult.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/FindItemAction.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/resource/CitizenResourceProvider.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/resource/LogResourceProvider.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/behavior/target/CitizenTargetSearchService.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/farm/CitizenFarmInventory.java`

**Interfaces:**
- `CitizenItemAction.tick(CitizenBehaviorContext): CitizenItemActionResult` returns `RUNNING`, `COMPLETE`, `UNAVAILABLE`, or `FAILED`, with a display status.
- `FindItemAction(Item item, int quantity, List<CitizenResourceProvider> providers)` first checks household storage; personal Citizens use their own inventory.
- `CitizenResourceProvider` exposes a stable provider ID, `canProvide(Item)`, an incremental target finder, and a validated gather operation.
- `LogResourceProvider` provides vanilla overworld log items by harvesting a valid natural log at the found location and inserting the actual drops into Citizen inventory before depositing them in household storage.

- [ ] **Step 1: Add a small reusable action result and interface**

Define the four terminal/progress states and a status string. Keep action progress instance-local; do not retain `Level`, `Container`, or other world references across ticks.

- [ ] **Step 2: Add the log resource provider**

Use a `CitizenTargetFinder` for eligible log blocks and a stable target key such as `resource:logs:<provider-id>`. Delegate incremental route, local-first target lookup, family memory, 256-block radius, and stale-location clearing to `CitizenTargetSearchService`. Before harvest, check that the target chunk is loaded and the block still qualifies. Collect block drops through the server block drop API, check carry capacity first, then deposit drops through `HouseholdStorage`.

- [ ] **Step 3: Add `FindItemAction` storage and provider flow**

On each tick, compute the shortage from the household chest count and requested quantity. Select the first registered provider that can supply the requested item. If no provider is available, return `UNAVAILABLE` with the item name. If inventory capacity is insufficient, deposit carried resources before seeking more. Use Citizen inventory accessors for transit stacks; add focused insert/extract helpers to `CitizenEntity` if required rather than exposing mutable list internals.

- [ ] **Step 4: Review persistence and item conservation**

Confirm carried materials use the existing saved `CitizenFarmInventory`, chest contents use vanilla block entity saves, and every harvest/transfer branch accounts for remainders. Walk through a remembered log that is removed, a missing provider, and insufficient carry capacity.

### Task 4: Implement recipe resolution and craft-item action

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/CraftItemAction.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/RecipeCraftingPlan.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/CraftingStationService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/action/FindItemAction.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdStorage.java`

**Interfaces:**
- `CraftItemAction(Item item, int quantity, List<CitizenResourceProvider> providers)` implements `CitizenItemAction` and recursively requests missing recipe inputs through `FindItemAction` or another `CraftItemAction`.
- `RecipeCraftingPlan.resolve(ServerLevel level, ItemStack requested, int maxDepth): Optional<RecipeCraftingPlan>` returns a bounded plan with concrete recipe inputs, output count, and required station. It detects cycles by item/recipe key and rejects unresolved ingredients.
- `CraftingStationService.ensureAvailable(CitizenBehaviorContext, RecipeCraftingPlan): CitizenItemActionResult` handles a crafting table requirement at the assigned home site.

- [ ] **Step 1: Resolve a registered recipe without mutating inventories**

Use Minecraft's active recipe manager and registry-aware crafting input. Match recipes against the requested output, record exact ingredients and output count, and bound recursive dependencies by depth and visited item/recipe keys. Return no plan when there is no compatible registered recipe.

- [ ] **Step 2: Implement the crafting station prerequisite**

For a plan that needs a crafting table, check the household chest for one. If absent, craft one through the same generic craft action, take it from the chest, and place it at the stable home crafting position. Validate loaded terrain, replaceability, support, and collision before placement. Personal Citizens without homes report a missing station if the request cannot be completed safely.

- [ ] **Step 3: Implement staged crafting**

For each missing ingredient, first use existing chest stock, then recursively craft a registered intermediate item, then ask registered resource providers to find gatherable raw inputs. Withdraw only the needed ingredients, create the crafting input, match the recipe again immediately before crafting, and verify output can be stored before consuming inputs. Deposit outputs in the home chest between dependency stages.

- [ ] **Step 4: Review recipe failure and conservation cases**

Trace no recipe, cycle, depth limit, recipe change before execution, missing provider, insufficient output space, and repeated reload between stages. Plans must recompute from current chest contents and must not consume ingredients until execution is valid.

### Task 5: Implement generic placement and the composter behavior

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/action/PlaceItemAction.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/ComposterBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/decision/RoutineDecisionRules.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/action/CraftItemAction.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/action/FindItemAction.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdWorkClaims.java`
- Reuse: `src/main/java/com/jedts/theeconomist/citizen/farm/NaturalWater.java`

**Interfaces:**
- `PlaceItemAction(Item item, int quantity, CitizenTargetFinder siteFinder)` implements `CitizenItemAction`, withdraws only after finding a valid site, and revalidates the site before placing.
- `ComposterBehavior` requests one `Items.COMPOSTER` per household and exposes the active child action's status.

- [ ] **Step 1: Add the generic placement action**

Search locally then through the existing household target search for a site accepted by the supplied finder. Navigate incrementally, revalidate the site and item count, withdraw one item, place the corresponding block, and return any placement remainder to storage. Clear invalid site memory and continue search.

- [ ] **Step 2: Define a composter farm-site finder**

Find a replaceable supported block near the household's existing farm area. Avoid occupied blocks and preserve required water access for nearby farmland. Before collecting materials, search for an existing composter in the accepted area and complete if one is already usable.

- [ ] **Step 3: Add behavior eligibility and household claim flow**

Register `ComposterBehavior` in `CitizenBehaviorRegistry` and add the minimum routine scoring rule needed to make it eligible for adult working Citizens with a household and farm area. Use one stable household request ID for the composter. Claim before manipulating shared stock, renew during progress, and release on complete, cancellation, or failure. Compose the actions in order: `CraftItemAction(COMPOSTER, 1)`, then `PlaceItemAction(COMPOSTER, 1, farmSiteFinder)`; the craft action itself composes `FindItemAction` for missing raw resources.

- [ ] **Step 4: Expose useful progress status**

Return statuses that name the item and current stage, including finding logs, storing materials, crafting an intermediate item, waiting for the station, and placing the composter. Unavailable and failure results must include the reason and remain retryable when world conditions change.

- [ ] **Step 5: Review household completion and interruption**

Walk through two family members becoming eligible together, one member being interrupted by sleep/emergency behavior, and another member taking over after the lease expires. Verify the household stops requesting composters after one has been placed.

### Task 6: End-to-end manual verification

**Files:**
- No new test files; follow the approved spec's verification approach during implementation review.

- [ ] **Step 1: Check a new household with an empty shared chest**

In a fresh world, load a generated home, confirm its assigned chest position, then observe a Citizen locate logs within 256 horizontal blocks, gather them, deposit them, craft the required intermediates, and place one composter by the farm.

- [ ] **Step 2: Check existing household save migration**

Load a world saved before storage coordinates existed. Confirm a loaded house repairs its missing coordinate and initializes storage without duplicating residents or replacing another block.

- [ ] **Step 3: Check interruption, storage failure, and recovery**

Interrupt a Citizen during a request, temporarily obstruct or fill the chest, remove a remembered log target, and reload between stages. Confirm visible statuses, no duplicated or lost items, stale memory invalidation, and eventual continuation by the same or another household member.

- [ ] **Step 4: Check the no-household compatibility path**

Give a player-created Citizen a supported item request. Confirm its own saved inventory is used and that it does not claim or write to a nearby household chest.
