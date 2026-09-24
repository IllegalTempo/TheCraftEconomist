# Generated Citizen Households Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate Economist houses in new Overworld terrain and populate each once with two to four Citizens who share a saved surname, while removing Citizen home signs.

**Architecture:** A registered single-piece structure places a data-driven house template after validating terrain and supplies an anchor. A dimension-scoped saved household ledger chooses the surname and resident slots; a server-side coordinator observes loaded structure starts and issues those slots without loading other chunks. Natural residents receive their surname and house home anchor before their first tick. Existing Citizens and explicit spawn paths keep their existing identity behavior.

**Tech Stack:** Minecraft 26.3, Fabric API lifecycle events, Java 25, Gradle, JUnit 5, Fabric GameTest, vanilla structure/template resources.

**Spec:** `docs/superpowers/specs/2026-09-22-generated-citizen-households-design.md`

## Global Constraints

- House generation is Overworld only, on suitable dry, reasonably level land; water is optional.
- A generated house starts two to four Citizens, all with the same saved surname. Spawn eggs and `/summon` remain independent.
- The structure set defaults to spacing 24 chunks and separation 8 chunks.
- A resident death never refills its issued slot. Existing saved Citizens retain their home and name.
- No new Citizen receives or places a home sign; existing placed or inventoried signs remain ordinary blocks/items.
- This workspace currently has no `.git`; do not invent commits or initialize a repository to satisfy plan bookkeeping.

## Review Focus

- A chunk reload must not issue a second Citizen for a recorded resident slot; Task 2 tests repeat issuance after codec round-trip.
- A house candidate beside water or on a cliff must be rejected without flattening terrain or placing blocks; Task 1 tests the site rule.
- A temporarily blocked spawn tile must leave its slot unissued and retry later; Task 4 tests that transition.
- Two different houses may select the same name from a short config list, while every resident of each one must match its own ledger entry; Task 4 tests both houses.
- A preexisting Citizen with a saved home and old sign state must keep its home and name and receive no new sign; Task 3 tests old NBT loading.

---

### Task 1: A registered house structure on suitable land

**Files:**
- Create: `src/main/resources/data/theeconomist/worldgen/structure/citizen_house.json`
- Create: `src/main/resources/data/theeconomist/worldgen/structure_set/citizen_houses.json`
- Create: `src/main/resources/data/theeconomist/tags/worldgen/biome/has_structure/citizen_house.json`
- Create: `src/main/resources/data/theeconomist/structure/citizen_house.nbt`
- Create: `tools/generate_citizen_house.py`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/CitizenHouseStructure.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/CitizenHousePiece.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/CitizenHouseStructures.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HouseSiteRules.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/house/HouseSiteRulesTest.java`
- Test: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenHouseGameTests.java`

**Interfaces:**
- Consumes: vanilla world generation structure registries and terrain heightmaps.
- Produces: registered `theeconomist:citizen_house` structure and piece types, plus `HouseSiteRules.accepts(...)` for checking a dry, solid, level footprint around the transformed entrance.

- [ ] **Step 1: Write the failing site-rule test.** Pin dry flat ground, one-block slope acceptance, water/cliff rejection, and a clear entrance. The seam takes a sampled `HouseSiteRules.Site` so the test does not need a live world.

```java
assertTrue(HouseSiteRules.accepts(new Site(true, 1, true)));
assertFalse(HouseSiteRules.accepts(new Site(false, 0, true))); // water
assertFalse(HouseSiteRules.accepts(new Site(true, 3, true)));  // cliff
assertFalse(HouseSiteRules.accepts(new Site(true, 0, false))); // blocked door
```

- [ ] **Step 2: Run `./gradlew test --tests '*HouseSiteRulesTest'` and confirm the new type/test fails.**
- [ ] **Step 3: Add the minimal site rule and sample world-generation terrain.** `Site` records solid dry footing, maximum footprint height difference, and an open entrance. Reject a candidate when any sample is missing; never force load a finished chunk. Check known occupied structure sites separately when generation metadata is available.

```java
public static boolean accepts(Site site) {
    return site.drySolidGround() && site.maxHeightDifference() <= 2
            && site.openEntrance();
}
```

- [ ] **Step 4: Register a custom `Structure` and `TemplateStructurePiece`.** `CitizenHouseStructure` uses `Structure.simpleCodec(CitizenHouseStructure::new)`, samples `WORLD_SURFACE_WG` heights over the rotated 9×7 footprint, calls `HouseSiteRules`, and returns `Optional.empty()` for invalid terrain. For valid terrain it returns one `GenerationStub` whose builder adds one `CitizenHousePiece`. The piece loads `theeconomist:citizen_house.nbt`, applies rotation, and exposes `entrance()` by transforming a fixed local template coordinate; its saved origin and rotation make that position stable after reload. Register the structure type in `BuiltInRegistries.STRUCTURE_TYPE` and the piece type in `BuiltInRegistries.STRUCTURE_PIECE`; call the registration method from `CitizenModule` before worlds load.

```java
public static final MapCodec<CitizenHouseStructure> CODEC = Structure.simpleCodec(CitizenHouseStructure::new);
@Override public StructureType<?> type() { return CitizenHouseStructures.HOUSE_TYPE; }
```

- [ ] **Step 5: Add the worldgen resources.** The structure JSON uses `type: theeconomist:citizen_house`, `step: surface_structures`, and the land-biome tag. The structure set uses random spread, spacing 24, separation 8, and a fixed new salt. The biome tag lists suitable vanilla Overworld land biomes, with data-pack extension allowed. Make a 9×7 oak/stone template with a solid floor, roof, door, lighting, and four beds; record the door's local coordinate in `CitizenHousePiece`. `tools/generate_citizen_house.py` should define a palette of block-state names, emit one palette index and position per occupied block, include Minecraft's `size`, `palette`, `blocks`, and `entities` NBT fields, and gzip the result to the resource path. Run the script twice and compare hashes so the asset is reproducible.

```json
{"placement":{"type":"minecraft:random_spread","salt":19218473,"separation":8,"spacing":24},"structures":[{"structure":"theeconomist:citizen_house","weight":1}]}
```

- [ ] **Step 6: Run `./gradlew runGameTest` with a resource-loading GameTest.** Assert the house structure key and template resolve, place the template in the test world, and verify door, roof, four beds, and transformed entrance coordinate. Add a fresh-world smoke check for natural placement on suitable land.

### Task 2: Persistent household slots and surname

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/Household.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdLedger.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HouseholdSavedData.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/house/HouseholdLedgerTest.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/house/HouseholdSavedDataTest.java`

**Interfaces:**
- Consumes: dimension-local structure anchor `BlockPos`, configured family-name list, and a random source.
- Produces: `HouseholdLedger.getOrCreate(BlockPos, List<String>, RandomGenerator)`, `Household.unissuedSlots()`, `Household.issue(int)`, and `HouseholdSavedData.TYPE`.

- [ ] **Step 1: Write failing tests.** For a new anchor, count is always 2–4 and surname is one of the configured names. Repeating `getOrCreate` keeps count/surname. Issuing slot 0 twice returns success once. Serializing and rebuilding the ledger preserves all issued slots. A second anchor is independent.

```java
Household first = ledger.getOrCreate(anchor, List.of("River", "Stone"), random);
assertTrue(first.residentCount() >= 2 && first.residentCount() <= 4);
assertTrue(first.issue(0));
assertFalse(first.issue(0));
Household loaded = restored.get(anchor).orElseThrow();
assertEquals(first.surname(), loaded.surname());
assertEquals(first.residentCount(), loaded.residentCount());
assertFalse(loaded.issue(0));
```

- [ ] **Step 2: Run `./gradlew test --tests '*Household*Test'` and confirm RED.**
- [ ] **Step 3: Implement a household value with mutable issued-slot state and a mutable ledger.** Give each slot a stable UUID derived from dimension/anchor/slot; persist a bit mask of issued slots. `getOrCreate` must use the current configured names only for a new house. Validate nonblank surname, count 2–4, and slot bounds. Construct the ledger with its dimension identifier.

```java
UUID slotId = UUID.nameUUIDFromBytes(
        (dimensionId + ":" + anchor.asLong() + ":" + slot).getBytes(StandardCharsets.UTF_8));
```

- [ ] **Step 4: Persist via `SavedDataType` and a codec, following `PlotClaimSavedData`.** Load with `level.getDataStorage().computeIfAbsent(HouseholdSavedData.TYPE)`. Mark dirty whenever a house is created or a slot is issued. Include dimension in the stable ID input even though saved data is dimension scoped.
- [ ] **Step 5: Re-run focused tests and verify GREEN.**

### Task 3: Natural resident identity, home, and sign removal

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenRuntime.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenIdentityFactory.java`
- Remove: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenHomeSign.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/identity/CitizenIdentityFactoryTest.java`
- Test: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenHouseGameTests.java`
- Modify: sign-related cases in `src/gametest/java/com/jedts/theeconomist/gametest/CitizenFarmingTradeGameTests.java`

**Interfaces:**
- Consumes: a house slot UUID, surname, and home anchor.
- Produces: `CitizenRuntime.createHouseIdentity(UUID, String, Set<String>)` and `CitizenEntity.assignHousehold(UUID houseId, String surname, Set<String> usedGivenNames, BlockPos home)` called before insertion into the world. The existing constructor, spawn egg, and summon paths remain unchanged except that they no longer issue signs.

- [ ] **Step 1: Write failing tests.** A new Citizen has one wooden hoe and zero oak signs. A natural resident assigned to a household has the supplied surname and saved house home. A spawn-egg Citizen keeps an independently generated family name. Loading old NBT with `HomeSignIssued=false` does not add a sign or overwrite an existing saved name/home.

```java
assertEquals(0, newCitizen.farmInventory().count(Items.OAK_SIGN));
resident.assignHousehold(houseId, "River", Set.of(), entrance);
assertEquals("River", resident.identity().familyName());
assertEquals(entrance, resident.homeSpot().orElseThrow());
```

- [ ] **Step 2: Run focused unit tests and `./gradlew runGameTest`; confirm RED.**
- [ ] **Step 3: Add a factory overload for a natural resident's surname and used given names.** `CitizenIdentityFactory.createForHouse(UUID citizenId, CitizenConfig config, String surname, Set<String> usedGivenNames)` chooses an unused configured given name when one exists and otherwise chooses from the full configured list. Preserve citizen UUID, life stage, appearance, and profile. `assignHousehold` sets the resident's house ID, updates the visible name, and calls the existing home setter. Persist house ID and home in entity NBT; old NBT without a house ID still loads. Do not recompute saved identity on reload.

```java
CitizenIdentity generated = CitizenRuntime.createHouseIdentity(getUUID(), surname, usedGivenNames);
identity = new CitizenIdentity(generated.schemaVersion(), getUUID(),
        generated.givenName(), surname, generated.lifeStage(),
        generated.profileUsername(), generated.appearance());
setCustomName(Component.literal(identity.displayName()));
setHomeSpot(home);
```

- [ ] **Step 4: Remove all home-sign issuance and placement paths.** Delete starter `OAK_SIGN` insertion, `homeSignIssued`, `facingHomeSign`, tick-time sign placement, the `HomeSignIssued` save field and migration, and `CitizenHomeSign`. Leave old inventory contents and world signs untouched. The ordinary fallback home selector remains for Citizens without a saved house home.
- [ ] **Step 5: Run focused tests and GameTests; confirm GREEN.**

### Task 4: Population coordinator for loaded houses

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/house/HousePopulationService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Test: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenHouseGameTests.java`

**Interfaces:**
- Consumes: the registered house structure key/start, `HouseholdSavedData`, and `CitizenEntity.assignHousehold(...)`.
- Produces: `HousePopulationService.register()` and public `populateLoadedHouse(ServerLevel, BlockPos anchor)` for GameTests.

- [ ] **Step 1: Write failing GameTests.** Given a house anchor and safe ground, first activation creates 2–4 Citizens within eight blocks and all surnames equal the saved household surname. A second activation creates none. Killing one and activating again does not replace it. Block all candidate tiles to leave slots unissued, then clear a tile and confirm retry. Create two houses and verify each household's residents match its own entry.

```java
service.populateLoadedHouse(level, anchor);
int firstCount = residentsNear(level, anchor).size();
assertTrue(firstCount >= 2 && firstCount <= 4);
service.populateLoadedHouse(level, anchor);
assertEquals(firstCount, residentsNear(level, anchor).size());
```

- [ ] **Step 2: Run `./gradlew runGameTest` and confirm RED.**
- [ ] **Step 3: Register `ServerChunkEvents.CHUNK_LOAD` and queue only chunks containing this structure's start/anchor.** Read `LevelChunk.getAllStarts()`, select the `CitizenHouseStructure` entry, and obtain the entrance from its saved `CitizenHousePiece`. Process a small fixed number of queued anchors on `ServerTickEvents.END_LEVEL_TICK`. Re-check that the Overworld level and anchor chunk are still loaded before work. Never classify a player-built copy as a generated house and never scan arbitrary terrain for houses.

```java
ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNew) -> queueHouseStarts(level, chunk));
ServerTickEvents.END_LEVEL_TICK.register(level -> processQueued(level, 4));
```

- [ ] **Step 4: Make issuance safe and bounded.** Choose up to four dry, empty, supported positions by the entrance within eight blocks, avoiding existing entities and any unloaded tile. Gather the already issued residents' given names to pass to `createForHouse`, then assign the stable slot UUID, surname, and home before `addFreshEntity`. Mark the slot issued only after insertion succeeds. A failed slot stays pending for later chunk-load retry. A recorded slot stays issued after death. Limit attempts per tick and ensure duplicate queue entries cannot issue twice.

```java
if (safeSpot.isPresent() && level.addFreshEntity(resident)) {
    household.issue(slot);
    savedData.setDirty();
}
```

- [ ] **Step 5: Re-run GameTests, including normal save/reload of the ledger and a resident.** Confirm no duplicates and no forced chunk loads.

### Task 5: User-facing description and final verification

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-22-generated-citizen-households-design.md` only if implementation reveals a reviewed detail that needs correction.

**Interfaces:** No new runtime API.

- [ ] **Step 1: Update README's Citizen farming paragraph.** Replace the old “home spot, no house structure, one sign” description with generated houses, two-to-four residents, shared surnames, independent spawn eggs, house-centered farming, and no sign behavior. State that existing explored chunks are not changed.
- [ ] **Step 2: Run `./gradlew build` and inspect full output.** It must compile the production code, run JUnit and GameTests, and produce `build/libs/theeconomist-0.1.0.jar`.
- [ ] **Step 3: Run one fresh-world manual smoke check.** Locate a generated house, verify a safe door and four beds, inspect all residents' names, unload/reload, and verify population does not increase. If a generated house fails to appear at the expected structure spacing, inspect the resource registry logs before calling the feature complete.
- [ ] **Step 4: Review the changed files against the spec and report any limitation.** This workspace has no Git metadata, so there is no commit or branch integration step; deliver the built JAR and exact test evidence.
