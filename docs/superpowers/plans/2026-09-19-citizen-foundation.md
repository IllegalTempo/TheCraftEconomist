# Citizen Entity Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent, player-shaped custom Citizen entity with configurable generated names, cached profile skins, deterministic fallback appearance, passive survival AI, a spawn egg, and an operator reload command.

**Architecture:** Separate pure identity/configuration logic from the Minecraft entity adapter and separate asynchronous profile resolution from client rendering. The server owns identity and signed appearance data; tracking clients receive only persisted appearance state and render it through a client-only entry point.

**Tech Stack:** Java 25, Minecraft 26.3 Mojang mappings, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Gson supplied by Minecraft, JUnit Jupiter 5.13.4, Fabric GameTest

**Spec:** `docs/superpowers/specs/2026-09-19-citizen-secure-trade-foundation-design.md`

**Depends on:** `docs/superpowers/plans/2026-09-19-currency-foundation.md` through its ordered `CurrencyModule` entry in `ModModules`.

## Global Constraints

- Entity ID is exactly `theeconomist:citizen`; Citizens coexist with vanilla villagers.
- Minecraft is exactly 26.3; Fabric Loader is 0.19.5; Fabric API is 0.161.0+26.3; Java release is 25.
- Citizens are created only by their spawn egg or `/summon` in this milestone.
- Every Citizen has a stable UUID, generated given/family name, `ADULT` life stage, and versioned identity data.
- Skin usernames come from `config/theeconomist/citizens.json`; generated identity never copies the skin username.
- Configuration reload is atomic and affects only Citizens created after the reload.
- Empty or failed profile lookups use a deterministic Steve/Alex-style fallback and never block a server tick.
- Citizens have 20 health, flee when hurt, avoid hazards, wander, look around, and never attack.
- Clothing is represented by standard armor/equipment; custom clothing assets are outside this milestone.
- Common code must not load any `net.minecraft.client` class on a dedicated server.

## Review Focus

- A malformed reload must not replace the last valid config; Task 1 tests state retention and the exact error path.
- Duplicate profile usernames differing only by case can defeat rate limiting; Task 1 tests case-insensitive normalization and stable first-entry retention.
- A completed lookup after entity removal or identity replacement can corrupt another Citizen; Task 4 tests identity token matching before applying results.
- Signed texture values are large and sensitive to truncation; Task 3 round-trips full values/signatures through entity persistence.
- Slim/wide rendering errors are only visible client-side; Task 5 verifies both models, fallback selection, armor, and dedicated-server isolation.

---

### Task 1: Implement validated, atomic Citizen configuration

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/config/CitizenConfig.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/config/CitizenConfigCodec.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/config/CitizenConfigService.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/config/ConfigReloadResult.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/config/CitizenConfigCodecTest.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/config/CitizenConfigServiceTest.java`

**Interfaces:**
- Consumes: a UTF-8 JSON file at a caller-provided `Path`.
- Produces: `CitizenConfig(List<String> profileUsernames, List<String> givenNames, List<String> familyNames)`, `CitizenConfig.defaults()`, `CitizenConfigCodec.decode(String): CitizenConfig`, `CitizenConfigService.current(): CitizenConfig`, and `CitizenConfigService.reload(): ConfigReloadResult`.

- [ ] **Step 1: Write codec tests for the accepted schema**

Use a JSON fixture containing whitespace, duplicates, and profile names differing by case. Assert exact normalized output:

```java
assertEquals(
        new CitizenConfig(
                List.of("ExamplePlayer", "SecondPlayer"),
                List.of("Amina", "Diego"),
                List.of("Patel", "Garcia")
        ),
        CitizenConfigCodec.decode("""
                {
                  "profileUsernames": [" ExamplePlayer ", "exampleplayer", "SecondPlayer"],
                  "givenNames": [" Amina ", "Amina", "Diego"],
                  "familyNames": ["Patel", " Garcia ", "Patel"]
                }
                """)
);
```

Add tests proving an empty profile list is valid, blank name entries are removed, empty usable name lists fail with the exact field name, malformed JSON fails, and unknown fields fail instead of being ignored.

- [ ] **Step 2: Run the codec test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.config.CitizenConfigCodecTest"`

Expected: test compilation fails because the configuration types do not exist.

- [ ] **Step 3: Implement the immutable configuration and strict decoder**

`CitizenConfig` copies every list with `List.copyOf`. Its built-in defaults use exactly these arrays:

```java
private static final List<String> DEFAULT_GIVEN_NAMES = List.of(
        "Amina", "Diego", "Haruto", "Leila", "Mateo", "Mei",
        "Nia", "Noor", "Priya", "Sofia", "Tariq", "Yuna"
);

private static final List<String> DEFAULT_FAMILY_NAMES = List.of(
        "Adebayo", "Dubois", "Garcia", "Haddad", "Ivanov", "Kim",
        "Nakamura", "Okafor", "Patel", "Silva", "Singh", "Wang"
);
```

Decode through a `JsonObject`, compare its key set with `profileUsernames`, `givenNames`, and `familyNames`, then normalize fields explicitly. Use a `LinkedHashMap<String, String>` keyed by `toLowerCase(Locale.ROOT)` for profile usernames and a `LinkedHashSet<String>` for names so the first declaration wins.

- [ ] **Step 4: Write service tests for startup and atomic reload**

Using `@TempDir`, cover:

```java
@Test
void failedReloadKeepsLastValidConfiguration(@TempDir Path directory) throws IOException {
    Path file = directory.resolve("citizens.json");
    Files.writeString(file, validConfig("Amina", "Patel"));
    CitizenConfigService service = CitizenConfigService.load(file);

    Files.writeString(file, "{\"givenNames\": [], \"familyNames\": [\"Garcia\"], \"profileUsernames\": []}");
    ConfigReloadResult result = service.reload();

    assertFalse(result.success());
    assertTrue(result.message().contains("givenNames"));
    assertEquals(List.of("Amina"), service.current().givenNames());
}
```

Also assert that a missing startup file creates a default JSON file, malformed startup uses built-in defaults while returning a diagnostic, and a successful reload replaces the snapshot exactly once.

- [ ] **Step 5: Implement atomic snapshot replacement**

Store the active value in `AtomicReference<CitizenConfig>`. Parse and validate into a local variable before calling `active.set(parsed)`. Return a record:

```java
public record ConfigReloadResult(boolean success, String message) {
    public static ConfigReloadResult ok() {
        return new ConfigReloadResult(true, "Citizen configuration reloaded");
    }

    public static ConfigReloadResult failed(String message) {
        return new ConfigReloadResult(false, message);
    }
}
```

Write defaults with UTF-8 and create only the `config/theeconomist` directory, never overwrite an existing malformed file.

- [ ] **Step 6: Run focused configuration tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.config.*"`

Expected: all configuration tests pass with zero failures.

- [ ] **Step 7: Commit configuration**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/config src/test/java/com/jedts/theeconomist/citizen/config
git commit -m "feat: add Citizen identity configuration"
```

### Task 2: Generate stable Citizen identities and fallback appearance

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenLifeStage.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenModelType.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenAppearance.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenIdentity.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/identity/CitizenIdentityFactory.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/identity/CitizenIdentityFactoryTest.java`

**Interfaces:**
- Consumes: `CitizenConfig`, a Citizen UUID, and an injected `RandomGenerator` used only for new identity choices.
- Produces: schema version 1 `CitizenIdentity`, `CitizenModelType.forFallback(UUID)`, and an optional assigned profile username.

- [ ] **Step 1: Write identity tests**

Pin the invariant without pinning an implementation-specific random sequence:

```java
@Test
void generatedIdentityUsesNamesButNeverUsesProfileAsDisplayName() {
    CitizenConfig config = new CitizenConfig(
            List.of("SkinAccount"), List.of("Amina"), List.of("Patel")
    );
    UUID citizenId = UUID.fromString("00000000-0000-0000-0000-000000000011");

    CitizenIdentity identity = new CitizenIdentityFactory(new Random(7)).create(citizenId, config);

    assertEquals(1, identity.schemaVersion());
    assertEquals(citizenId, identity.citizenId());
    assertEquals("Amina", identity.givenName());
    assertEquals("Patel", identity.familyName());
    assertEquals("Amina Patel", identity.displayName());
    assertEquals(CitizenLifeStage.ADULT, identity.lifeStage());
    assertEquals(Optional.of("SkinAccount"), identity.profileUsername());
    assertNotEquals("SkinAccount", identity.displayName());
}
```

Add tests for an empty profile pool, deterministic fallback model for the same UUID, both model types across two fixed UUID fixtures, and defensive null/blank rejection in persisted identity records.

- [ ] **Step 2: Run the identity test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.identity.CitizenIdentityFactoryTest"`

Expected: test compilation fails because the identity types do not exist.

- [ ] **Step 3: Implement the identity records**

Use these stable shapes:

```java
public enum CitizenLifeStage { ADULT }

public enum CitizenModelType {
    WIDE, SLIM;

    public static CitizenModelType forFallback(UUID citizenId) {
        return (citizenId.hashCode() & 1) == 0 ? WIDE : SLIM;
    }
}

public record CitizenAppearance(
        CitizenModelType modelType,
        Optional<UUID> profileId,
        Optional<String> textureValue,
        Optional<String> textureSignature
) {
    public static CitizenAppearance fallback(UUID citizenId) {
        return new CitizenAppearance(CitizenModelType.forFallback(citizenId), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
```

`CitizenIdentity` contains schema version, UUID, given name, family name, stage, optional profile username, and appearance. Its compact constructor validates schema version 1 and nonblank names. `displayName()` returns `givenName + " " + familyName`.

- [ ] **Step 4: Implement injected random selection**

`CitizenIdentityFactory#create(UUID, CitizenConfig)` selects each name with `random.nextInt(list.size())`, chooses a profile only when the pool is nonempty, and always installs `CitizenAppearance.fallback(citizenId)` first. Do not seed from game time inside the factory; the caller owns the random generator.

- [ ] **Step 5: Run identity tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.identity.*"`

Expected: all identity tests pass.

- [ ] **Step 6: Commit identity generation**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/identity src/test/java/com/jedts/theeconomist/citizen/identity
git commit -m "feat: generate persistent Citizen identities"
```

### Task 3: Register and persist the passive Citizen entity

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/CitizenRuntime.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntities.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenIdentityNbt.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/command/CitizenCommands.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/entity/CitizenIdentityNbtTest.java`
- Create: `src/gametest/java/com/jedts/theeconomist/citizen/CitizenEntityGameTest.java`
- Modify: `build.gradle`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`

**Interfaces:**
- Consumes: `CitizenConfigService`, `CitizenIdentityFactory`, and the already registered `CurrencyModule` ordering.
- Produces: `CitizenEntities.CITIZEN`, `CitizenEntities.CITIZEN_SPAWN_EGG`, `/theeconomist reload`, and a `CitizenEntity` whose `identity(): CitizenIdentity` is never null after server-side creation/load.

- [ ] **Step 1: Enable a separate Fabric GameTest source set**

Configure Loom's GameTest support and add `src/gametest/resources/fabric.mod.json` with a gametest entry point. Keep JUnit unit tests in `src/test`; do not make pure tests launch Minecraft.

- [ ] **Step 2: Inspect exact 26.3 entity and NBT signatures**

Run `javap` against the remapped 26.3 JAR for `PathfinderMob`, `EntityType.Builder`, `SpawnEggItem`, `CompoundTag`, and entity save/load hooks. Record the method descriptors in the task notes and use the Mojang-mapped names that exist in the local JAR. The domain interfaces in this plan remain unchanged if an adapter signature differs.

- [ ] **Step 3: Write NBT round-trip tests**

Create a fixed identity with a 2,000-character texture value and a nonempty signature. Assert:

```java
CompoundTag tag = CitizenIdentityNbt.write(identity);
CitizenIdentity restored = CitizenIdentityNbt.read(tag);
assertEquals(identity, restored);
assertEquals(identity.appearance().textureValue(), restored.appearance().textureValue());
assertEquals(identity.appearance().textureSignature(), restored.appearance().textureSignature());
```

Add a test that rejects an unsupported schema version with an error containing that version.

- [ ] **Step 4: Run the NBT test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.entity.CitizenIdentityNbtTest"`

Expected: test compilation fails because `CitizenIdentityNbt` does not exist.

- [ ] **Step 5: Implement explicit versioned NBT fields**

Write under one root key `TheEconomistCitizen` with exact children `SchemaVersion`, `CitizenId`, `GivenName`, `FamilyName`, `LifeStage`, `ProfileUsername`, `ModelType`, `ProfileId`, `TextureValue`, and `TextureSignature`. Omit absent optional values rather than writing empty strings. Decode enum names with precise field errors and never silently create a different identity from malformed persisted data.

- [ ] **Step 6: Write entity game tests before entity registration**

Add GameTests that spawn `theeconomist:citizen` and assert 20 maximum health, generated custom name, `ADULT` identity, no target after several ticks, survival after ordinary idle ticks, and identity equality after save/load. Add a test that runs the `/summon theeconomist:citizen` command. Add a spawn-egg test that confirms a new identity is generated rather than copied from another Citizen.

- [ ] **Step 7: Implement entity type, attributes, and passive goals**

Register a `PathfinderMob`-based type sized to a player. Register attributes with maximum health 20, movement speed 0.25, and follow range 24. In `registerGoals`, add only swimming/float, panic after damage, hostile-mob avoidance, random strolling, looking at nearby players/living entities, and random looking. Do not add a target selector or attack goal.

On first server-side finalization, call the current `CitizenIdentityFactory` snapshot once, store the result, set the visible custom name, and synchronize compact render fields. On load, restore the persisted identity instead of generating a replacement.

- [ ] **Step 8: Register the spawn egg and reload command**

Register `theeconomist:citizen_spawn_egg` through the same registry-owner pattern as Crown items. The command callback requires permission level 3, invokes `CitizenConfigService.reload()`, sends its exact result message, and returns `1` for success or `0` for failure:

```java
literal("theeconomist")
        .requires(source -> source.hasPermission(3))
        .then(literal("reload").executes(context -> CitizenRuntime.reload(context.getSource())));
```

- [ ] **Step 9: Add `CitizenModule` after `CurrencyModule`**

`CitizenModule.initialize()` constructs the runtime once, registers the entity, attributes, spawn egg, command callback, and server lifecycle cleanup. `ModModules` must be exactly ordered as Currency then Citizen at this stage.

- [ ] **Step 10: Run unit and GameTests**

Run:

```powershell
./gradlew.bat test --tests "com.jedts.theeconomist.citizen.*"
./gradlew.bat runGameTest
```

Expected: NBT tests and Citizen GameTests pass; no Citizen attacks any entity.

- [ ] **Step 11: Commit server-side Citizen entity**

```powershell
git add build.gradle src/main/java/com/jedts/theeconomist/citizen src/main/java/com/jedts/theeconomist/core/module/ModModules.java src/test/java/com/jedts/theeconomist/citizen src/gametest
git commit -m "feat: add passive Citizen entity"
```

### Task 4: Resolve and cache profile appearances asynchronously

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/skin/ProfileResolver.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/skin/ResolvedProfile.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/skin/ProfileLookupService.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/skin/MinecraftProfileResolver.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/skin/ProfileLookupServiceTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenRuntime.java`

**Interfaces:**
- Consumes: `ProfileResolver.resolve(String): CompletionStage<Optional<ResolvedProfile>>` and a Citizen identity token of `(citizenId, assignedUsername)`.
- Produces: bounded cached resolution and `CitizenEntity#applyResolvedProfile(UUID, String, ResolvedProfile): boolean`.

- [ ] **Step 1: Define the adapter boundary and resolved result**

```java
public interface ProfileResolver {
    CompletionStage<Optional<ResolvedProfile>> resolve(String username);
}

public record ResolvedProfile(
        UUID profileId,
        String textureValue,
        String textureSignature,
        CitizenModelType modelType
) {
}
```

Reject blank usernames, blank texture values, and missing signatures in the record constructor. Normalize cache keys with `trim().toLowerCase(Locale.ROOT)`.

- [ ] **Step 2: Write cache, timeout, and stale-result tests with a fake resolver**

Use a manually controlled `CompletableFuture` and fake clock/scheduler. Test concurrent requests for `Example` and `example` share one resolver call, successes remain cached, failures are negatively cached until expiry, timeouts produce `Optional.empty()`, and resolver completion never runs on the caller's server-tick thread.

For stale entity protection, create identity A, begin lookup, replace the entity's identity token with B, complete A, and assert `applyResolvedProfile(A.id(), A.username(), result)` returns false without mutating B.

- [ ] **Step 3: Run the focused test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.citizen.skin.ProfileLookupServiceTest"`

Expected: test compilation fails because the profile service types do not exist.

- [ ] **Step 4: Implement bounded cache behavior**

Use one `ConcurrentHashMap<String, CacheEntry>` for completed entries and one for in-flight futures. Apply a 5-second lookup timeout, a 24-hour positive TTL, a 10-minute negative TTL, and a semaphore capped at two concurrent external resolutions. Always remove a completed future from the in-flight map. These constants live in named fields and can be reduced through package-private constructor arguments in tests.

- [ ] **Step 5: Implement the Minecraft resolver adapter**

Use the server's supported profile/session services from the 26.3 API; do not send direct unauthenticated HTTP requests. Require a signed `textures` property. Derive `SLIM` only from the texture metadata model value; otherwise use `WIDE`. Log username, result category, and elapsed time, but never log texture values or signatures.

- [ ] **Step 6: Apply results only on the server executor**

After the async stage completes, schedule entity mutation on `MinecraftServer#execute`. Re-fetch the entity by UUID or retain a safe weak identity reference, then verify entity existence, Citizen UUID, and assigned username before replacing only the appearance portion of the identity. Persist and synchronize the new value once.

- [ ] **Step 7: Run profile tests and GameTests**

Run:

```powershell
./gradlew.bat test --tests "com.jedts.theeconomist.citizen.skin.*"
./gradlew.bat runGameTest
```

Expected: cache tests pass; GameTests still pass with a resolver that always returns empty; Citizen creation never waits for lookup completion.

- [ ] **Step 8: Commit asynchronous profile resolution**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/skin src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java src/main/java/com/jedts/theeconomist/citizen/CitizenRuntime.java src/test/java/com/jedts/theeconomist/citizen/skin
git commit -m "feat: resolve Citizen profile skins"
```

### Task 5: Add the client renderer and Citizen resources

**Files:**
- Create: `src/client/java/com/jedts/theeconomist/client/TheEconomistClient.java`
- Create: `src/client/java/com/jedts/theeconomist/client/citizen/CitizenRenderer.java`
- Create: `src/client/java/com/jedts/theeconomist/client/citizen/CitizenRenderState.java`
- Create: `src/client/java/com/jedts/theeconomist/client/citizen/CitizenSkinCache.java`
- Create: `src/client/resources/assets/theeconomist/textures/entity/citizen/steve.png`
- Create: `src/client/resources/assets/theeconomist/textures/entity/citizen/alex.png`
- Create: `src/main/resources/assets/theeconomist/items/citizen_spawn_egg.json`
- Create: `src/main/resources/assets/theeconomist/models/item/citizen_spawn_egg.json`
- Modify: `build.gradle`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/assets/theeconomist/lang/en_us.json`

**Interfaces:**
- Consumes: synchronized `CitizenIdentity` render fields and `CitizenEntities.CITIZEN`.
- Produces: client entry point `com.jedts.theeconomist.client.TheEconomistClient`, wide/slim player-shaped rendering, signed-skin texture cache, armor/equipment layers, and spawn-egg presentation.

- [ ] **Step 1: Split common and client source sets**

Configure Loom source sets so `src/client` is compiled only for the client environment. Add this entry point without changing the common one:

```json
"client": [
  "com.jedts.theeconomist.client.TheEconomistClient"
]
```

- [ ] **Step 2: Inspect exact 26.3 render-state APIs**

Run `javap` for `LivingEntityRenderer`, `HumanoidModel`, `HumanoidRenderState`, `PlayerRenderer`, `PlayerModel`, `EquipmentLayerRenderer`, and `EntityRendererProvider.Context`. Choose the supported wide/slim model construction and armor-layer adapter from those signatures. Record the selected constructors in the task notes before adding renderer code.

- [ ] **Step 3: Add deterministic fallback textures**

Ship one Steve-style wide fallback and one Alex-style slim fallback under the exact paths above. They must be original project assets or redistributable Minecraft-compatible references permitted for mod packaging; do not download or embed a third party's player skin without permission.

- [ ] **Step 4: Implement render-state extraction and texture selection**

The renderer copies display name, model type, equipment, pose, and appearance property into `CitizenRenderState`. `CitizenSkinCache` returns the correct packaged fallback immediately. For a signed appearance, it registers or retrieves a dynamic texture keyed by the hash of profile UUID plus signed texture value; failures return the same deterministic fallback and are negatively cached for the client session.

- [ ] **Step 5: Render wide/slim models and standard equipment layers**

Maintain one renderer/model path for `WIDE` and one for `SLIM`. Copy vanilla living animation state, render held items and armor with standard equipment layers, and leave clothing-specific layers absent. The generated Citizen display name remains the nameplate; never show the profile username.

- [ ] **Step 6: Add spawn-egg assets and localization**

Add `entity.theeconomist.citizen`, `item.theeconomist.citizen_spawn_egg`, and `/theeconomist reload` feedback keys. Add the 26.3 item-definition/model pair for the egg using the standard spawn-egg model and colors selected in registration.

- [ ] **Step 7: Build and perform client matrix checks**

Run `./gradlew.bat build` and `./gradlew.bat runClient`. Spawn fixed test Citizens for wide fallback, slim fallback, resolved wide, and resolved slim appearances. Equip helmet, chestplate, leggings, boots, and held items. Verify animations, nameplates, armor, and fallback behavior after deliberately failing a profile lookup.

- [ ] **Step 8: Run a dedicated-server isolation check**

Run `./gradlew.bat runServer`, wait for readiness, then stop it. Inspect logs and the common source-set bytecode to confirm no class under `com.jedts.theeconomist.client` or `net.minecraft.client` is loaded by common initialization.

- [ ] **Step 9: Commit client rendering and resources**

```powershell
git add build.gradle src/client src/main/resources/fabric.mod.json src/main/resources/assets/theeconomist src/main/java/com/jedts/theeconomist/citizen
git commit -m "feat: render player-shaped Citizens"
```

### Task 6: Document and verify the Citizen foundation

**Files:**
- Modify: `README.md`
- Verify: `config/theeconomist/citizens.json` generated in a development run

**Interfaces:**
- Consumes: completed Citizen config, entity, appearance, command, and renderer behavior.
- Produces: accurate operator and player documentation plus final evidence for this subsystem.

- [ ] **Step 1: Update README implemented-status text**

Document the exact config path and three JSON fields, the default name behavior, profile lookup/fallback rules, permission-level-3 `/theeconomist reload`, spawn egg, `/summon theeconomist:citizen`, passive/mortal behavior, and the rule that reload affects only future Citizens. Mark jobs, trading, households, children, natural spawning, and services as planned rather than implemented.

- [ ] **Step 2: Run the complete automated suite**

Run:

```powershell
./gradlew.bat clean test
./gradlew.bat runGameTest
./gradlew.bat build
```

Expected: all unit and GameTests pass, the remapped JAR builds, and no configuration or entity test is skipped.

- [ ] **Step 3: Perform restart persistence verification**

Start a development server, summon two Citizens, record their generated names/model types, stop cleanly, restart, and confirm their UUIDs, names, assigned usernames, texture values/signatures, health, and equipment persist unchanged. Reload a changed name config, summon a third Citizen, and verify only the new Citizen uses the new names.

- [ ] **Step 4: Perform failure verification**

Replace the runtime config with malformed JSON and run `/theeconomist reload`; verify the command reports the exact field error and the last valid config remains active. Test an empty profile pool and an invalid username; both Citizens must spawn immediately with deterministic fallbacks and survive restart.

- [ ] **Step 5: Commit documentation**

```powershell
git add README.md
git commit -m "docs: document Citizen foundation"
```

- [ ] **Step 6: Record acceptance evidence**

Record test counts, GameTest results, build result, dedicated-server result, client matrix result, restart result, and malformed-config result in the implementation handoff. Include any 26.3 adapter signatures that differed from the planning examples.
