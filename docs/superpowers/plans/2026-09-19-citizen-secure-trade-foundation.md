# Citizen and Secure Trade Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved first playable slice: custom Citizens, Crown currency, and a server-authoritative player-to-player trade flow with durable escrow and recovery.

**Architecture:** Keep pure configuration, identity, currency, request, session, and ledger rules in small testable domain classes. Fabric adapters register entities/items/commands, connect server lifecycle events, validate packets, and bridge authoritative state to client screens. The server owns all item stacks and state transitions; clients render snapshots and submit intent only.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Fabric Loom 1.17.21, Gradle 9.6, Gson supplied by Minecraft, JUnit Jupiter 5.13.4, Fabric GameTest.

**Spec:** `docs/superpowers/specs/2026-09-19-citizen-secure-trade-foundation-design.md`

**Supporting detailed plans:**

- `docs/superpowers/plans/2026-09-19-currency-foundation.md` defines the Crown domain, item registration, assets, and resource tests used by Task 2.
- `docs/superpowers/plans/2026-09-19-citizen-foundation.md` defines configuration, identity, entity, skin, renderer, and Citizen verification used by Task 3.
- `docs/superpowers/plans/2026-09-19-secure-player-trade.md` defines the trade pair, request, session, and escrow domain used by Tasks 4 and 5.

## Global Constraints

- Minecraft is exactly 26.3; Fabric Loader is 0.19.5; Fabric API is 0.161.0+26.3; Fabric Loom is 1.17.21; Gradle is 9.6; Java release is 25.
- Common code must not load `net.minecraft.client` classes on a dedicated server.
- Modules initialize in this order: `CurrencyModule`, `CitizenModule`, `TradeModule`.
- Entity ID is exactly `theeconomist:citizen`; Citizens coexist with vanilla villagers.
- Citizens are created only by their spawn egg or `/summon` in this milestone.
- Crown values are Copper=1, Silver=10, Gold=100; Crowns have no survival source yet.
- A trade request starts only from shift-right-clicking another player with an empty main hand.
- Requests expire after 15 seconds; request and active-session range is at most 16 blocks in one dimension.
- Both request acceptances are required before opening the trade screen.
- Each participant has exactly 18 public offer slots arranged 6 by 3; the remote offer is read-only.
- Both readiness flags start a 60-server-tick countdown; any accepted offer edit clears both flags.
- Death, disconnect, dimension change, excess distance, screen close, decline, timeout, and invalid state cancel; nonlethal damage does not.
- Successful overflow is dropped at the receiving player's feet; there is no mailbox.
- Escrow is persistent, owned by the original offering player, and never silently discarded.
- Every completion and return operation is idempotent and uses unique transaction/delivery identifiers.

## Review Focus

- Malformed or concurrently reloaded Citizen JSON must retain the last valid snapshot; Task 3 pins exact errors and atomic replacement.
- A profile lookup completing after a Citizen changes or unloads must not mutate another identity; Task 3's skin tests use identity tokens.
- Reverse-direction requests in the same tick must resolve to one pair lock; Task 4 tests concurrent `createOrReuse`.
- Creative/quick-move/drag/number-key/double-click menu actions must not touch remote slots; Task 6 tests every supported click path.
- A crash between delivery and marker persistence must converge to exactly one item with no silent loss; Task 5 tests each interruption boundary and overflow token.

## File Map

The implementation follows the existing `com.jedts.theeconomist` package layout.

- `src/main/java/com/jedts/theeconomist/currency/**`: denomination values, items, and currency module.
- `src/main/java/com/jedts/theeconomist/citizen/**`: config, identity, entity, skin service, commands, and module.
- `src/main/java/com/jedts/theeconomist/trade/domain/**`: canonical pairs and fixed rules.
- `src/main/java/com/jedts/theeconomist/trade/request/**`: request state and per-player locking.
- `src/main/java/com/jedts/theeconomist/trade/session/**`: offer revisions, readiness, countdown, and cancellation.
- `src/main/java/com/jedts/theeconomist/trade/persistence/**`: versioned escrow and saved data.
- `src/main/java/com/jedts/theeconomist/trade/delivery/**`: idempotent delivery/return and overflow ownership.
- `src/main/java/com/jedts/theeconomist/trade/network/**`: packet codecs, registration, and server validation.
- `src/main/java/com/jedts/theeconomist/trade/integration/**`: interaction hook, lifecycle bridge, and runtime coordinator.
- `src/main/java/com/jedts/theeconomist/client/**`: client entry point, Citizen renderer, request HUD, key bindings, and trade screen.
- `src/main/resources/assets/theeconomist/**`: item/entity models, textures, translations, and GUI sprites.
- `src/test/java/com/jedts/theeconomist/**`: pure domain, codec, and resource tests.
- `src/gametest/java/com/jedts/theeconomist/**`: entity, packet, and server lifecycle integration tests.

---

### Task 1: Stabilize shared test and module infrastructure

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Modify: `src/main/java/com/jedts/theeconomist/TheEconomistMod.java`
- Create: `src/test/java/com/jedts/theeconomist/test/FixedClock.java`
- Create: `src/test/java/com/jedts/theeconomist/test/ItemStackFixtures.java`

**Interfaces:**
- Consumes: the existing `TheEconomistModule` contract and `ModuleLoader` declaration order.
- Produces: deterministic test fixtures, a GameTest source set, and an explicit module list whose first entry is `CurrencyModule`.

- [ ] **Step 1: Add only the test dependencies and GameTest source set required by the approved spec.** Keep the Java toolchain at 25 and do not introduce a runtime library for domain state machines.
- [ ] **Step 2: Run `./gradlew.bat test` and record the existing scaffold baseline.** Expected: the current module tests pass before new feature tests are added.
- [ ] **Step 3: Add `FixedClock` and `ItemStackFixtures` with no Minecraft client imports.** `FixedClock` exposes `long now()` and `void advance(long ticks)`; fixtures create copies, full stacks, and empty slots for tests.
- [ ] **Step 4: Run `./gradlew.bat test` again.** Expected: baseline tests and fixture compilation pass.
- [ ] **Step 5: Commit `build.gradle`, test fixtures, and module-list preparation.** Use `git commit -m "test: prepare feature test infrastructure"`.

### Task 2: Implement Crown currency

**Files:**
- Create/modify: `src/main/java/com/jedts/theeconomist/currency/**`
- Create/modify: `src/main/resources/assets/theeconomist/**`
- Create/modify: `src/main/resources/data/theeconomist/tags/items/currency.json`
- Create: `src/test/java/com/jedts/theeconomist/currency/**`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: Fabric item registries and module initialization.
- Produces: `CrownDenomination`, `CrownItems`, `CrownValues.total(Iterable<ItemStack>)`, three registered item IDs, the currency tag, and a creative group.

- [ ] **Step 1: Write failing tests for the three exact denominations, stack values, and invalid non-currency valuation.** Pin `copper_crown=1`, `silver_crown=10`, `gold_crown=100`, stack limit 64, and a total computed only from registered identities.
- [ ] **Step 2: Run `./gradlew.bat test --tests "com.jedts.theeconomist.currency.*"`.** Expected: compilation fails because the denomination and item classes do not exist.
- [ ] **Step 3: Implement immutable denomination metadata and authoritative total calculation.** Reject null/unknown items and never read a client-provided value or arbitrary data component as currency.
- [ ] **Step 4: Register `theeconomist:copper_crown`, `theeconomist:silver_crown`, and `theeconomist:gold_crown` through `CurrencyModule`.** Add tooltip value text and expose the items in a creative group; do not add recipes or survival sources.
- [ ] **Step 5: Add item models, 16x16 RGBA textures, English translations, and `data/theeconomist/tags/items/currency.json`.** Resource tests must load all paths and verify each identifier occurs exactly once in the tag.
- [ ] **Step 6: Run focused tests, `./gradlew.bat build`, and a dedicated-server smoke test.** Expected: zero test failures, all resources in the JAR, and no client class loading on the server.
- [ ] **Step 7: Update README with IDs, values, `/give` examples, and the absence of survival sources.**
- [ ] **Step 8: Commit with `git commit -m "feat: add Crown currency foundation"`.**

### Task 3: Implement the Citizen foundation

**Files:**
- Create/modify: `src/main/java/com/jedts/theeconomist/citizen/**`
- Create/modify: `src/main/resources/assets/theeconomist/**` for Citizen renderer resources
- Create: `src/test/java/com/jedts/theeconomist/citizen/**`
- Create: `src/gametest/java/com/jedts/theeconomist/citizen/**`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Modify: `build.gradle`
- Modify: `README.md`

**Interfaces:**
- Consumes: `CitizenConfigService`, `CitizenIdentityFactory`, `CitizenIdentityNbt`, `ProfileLookupService`, and the ordered `CurrencyModule`.
- Produces: `CitizenEntities.CITIZEN`, `CitizenEntities.CITIZEN_SPAWN_EGG`, `/theeconomist reload`, `CitizenEntity.identity()`, and a client renderer supporting wide/slim/fallback/armor.

The detailed file-by-file TDD sequence is in `2026-09-19-citizen-foundation.md`; implement its six tasks in order. The following acceptance gates are mandatory:

- [ ] **Step 1: Test and implement strict config parsing.** Trim values, remove blanks, deduplicate profiles case-insensitively, reject unknown fields, retain the last valid snapshot on failed reload, and use built-in broad names when startup configuration is missing or malformed.
- [ ] **Step 2: Test and implement identity generation.** Persist schema version 1, UUID, given/family name, `ADULT`, optional skin username, and deterministic fallback model. The display name must never be the skin username.
- [ ] **Step 3: Test and implement NBT round trips.** Preserve long signed texture values and signatures exactly; reject unsupported schema versions with field-specific errors.
- [ ] **Step 4: Register the passive `PathfinderMob` entity, attributes, spawn egg, summon behavior, and permission-3 reload command.** Add only swimming, panic/flee, hazard avoidance, wandering, looking, and idle goals; no attack target or job logic.
- [ ] **Step 5: Implement asynchronous profile resolution with bounded timeout, positive/negative cache, rate limiting, persisted signed data, and identity-token checks before applying results.** A failed lookup leaves the deterministic fallback and never blocks a server tick.
- [ ] **Step 6: Register client rendering for wide/slim player models, nameplates, equipment, and armor.** Keep all renderer classes behind the client entry point.
- [ ] **Step 7: Run Citizen unit tests, GameTests, `./gradlew.bat build`, restart persistence checks, malformed reload checks, and a dedicated-server smoke test.**
- [ ] **Step 8: Commit with `git commit -m "feat: add passive Citizen foundation"`.**

### Task 4: Implement pure trade domain state

**Files:**
- Create/modify: `src/main/java/com/jedts/theeconomist/trade/domain/**`
- Create/modify: `src/main/java/com/jedts/theeconomist/trade/request/**`
- Create/modify: `src/main/java/com/jedts/theeconomist/trade/session/**`
- Create: `src/test/java/com/jedts/theeconomist/trade/domain/**`
- Create: `src/test/java/com/jedts/theeconomist/trade/request/**`
- Create: `src/test/java/com/jedts/theeconomist/trade/session/**`

**Interfaces:**
- Consumes: player UUIDs, canonical pairs, authenticated participant actions, offer revisions, and injected server ticks.
- Produces: `TradeRules.REQUEST_TIMEOUT_TICKS=300`, `MAX_DISTANCE_SQUARED=256.0`, `COUNTDOWN_TICKS=60`, `OFFER_SLOTS=18`, `TradeRequestRegistry`, and immutable `TradeSessionSnapshot`.

- [ ] **Step 1: Write failing tests for UUID pair canonicalization and fixed rules.** Assert reversed participants produce one `TradePair` and equal participants are rejected.
- [ ] **Step 2: Implement `TradePair`, `TradeClock`, and `TradeRules`; run focused tests and confirm all constants.**
- [ ] **Step 3: Write failing request tests for two independent acceptances, duplicate acceptance, nonparticipant rejection, decline, timeout at tick 300, and terminal idempotency.**
- [ ] **Step 4: Implement `TradeRequest` and `TradeRequestRegistry` with one lock over ID, pair, and participant indexes.** Concurrent reversed initiation must return one request UUID and terminal removal must release both participants.
- [ ] **Step 5: Write failing session transition tests for OPEN, COUNTDOWN, COMMITTING, COMPLETED, CANCELLING, CANCELLED, readiness reset, stale revisions, and slot bounds -1/18.**
- [ ] **Step 6: Implement `TradeSession` with synchronized mutations and immutable snapshots; `tick(now)` returns transitions but performs no inventory I/O.**
- [ ] **Step 7: Run all domain/request/session tests and commit with `git commit -m "feat: add trade request and session state"`.**

### Task 5: Implement persistent escrow and idempotent delivery

**Files:**
- Create/modify: `src/main/java/com/jedts/theeconomist/trade/persistence/**`
- Create/modify: `src/main/java/com/jedts/theeconomist/trade/delivery/**`
- Create: `src/test/java/com/jedts/theeconomist/trade/persistence/**`
- Create: `src/test/java/com/jedts/theeconomist/trade/delivery/**`

**Interfaces:**
- Consumes: authoritative `ItemStack` copies removed from player inventories, session/entry UUIDs, original owner UUIDs, intended recipients, and a storage/delivery adapter.
- Produces: versioned `TradeLedgerSavedData`, exactly 18-slot `EscrowOffer`s, durable delivery markers, `DeliveryToken`, and idempotent deliver-or-return operations.

- [ ] **Step 1: Write failing codec tests for both offers, empty slots, item components, all session states, delivery markers, duplicate entry IDs, invalid slots, nonpositive counts, and unsupported schema versions.**
- [ ] **Step 2: Implement defensive escrow records and supported 26.3 `ItemStack` serialization through the world registry provider.** Mark saved data dirty on every deposit, withdrawal, transition, and marker change.
- [ ] **Step 3: Write interruption tests at PREPARED, inventory insertion, player save, DELIVERED marker, escrow cleanup, and overflow spawn boundaries.** Restart after each failure and assert exactly one copy, no disappearing entry, and safe convergence.
- [ ] **Step 4: Implement `theeconomist:trade_delivery_token` and the prepare-scan-deliver-mark protocol.** Scan inventory and owned nearby overflow entities before adding anything; persist `DELIVERED` before escrow cleanup; retain offline returns until login.
- [ ] **Step 5: Test full-inventory overflow ownership, pickup protection, cancellation-to-original-owner, controlled shutdown cancellation, and restart recovery for OPEN/COUNTDOWN/CANCELLING/COMMITTING.**
- [ ] **Step 6: Run persistence and delivery tests and commit with `git commit -m "feat: add persistent trade escrow"`.**

### Task 6: Add authenticated network protocol and server runtime integration

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/network/TradePacketIds.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/network/TradePayloads.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/network/TradeNetworking.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/integration/TradeRuntime.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/integration/TradeInteractionHandler.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/integration/TradeLifecycleHandler.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/network/TradePacketValidationTest.java`
- Create: `src/gametest/java/com/jedts/theeconomist/trade/TradeRuntimeGameTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`

**Interfaces:**
- Consumes: `TradeRequestRegistry`, `TradeSession`, `TradeLedger`, `CrownValues`, authenticated `ServerPlayer`, and Fabric server lifecycle callbacks.
- Produces: packet handlers for request/accept/decline/offer/ready/cancel, a single `TradeRuntime` per server, and `TradeModule` initialized after CitizenModule.

- [ ] **Step 1: Write packet validation tests.** For every payload, assert the sender must be a session participant, the state must permit the action, slot indices are bounded, the sequence/revision is not stale, and the pair remains same-dimension and within 16 blocks. Assert malformed or duplicate payloads do not mutate state.
- [ ] **Step 2: Implement typed payload codecs containing only request/session IDs, action values, slot indexes, expected revisions, and intent flags.** Never deserialize a client-supplied authoritative `ItemStack`.
- [ ] **Step 3: Implement the shift-right-click empty-hand interaction.** Reject dead/disconnected/busy/different-dimension/out-of-range players; call `createOrReuse`; send a snapshot to both participants.
- [ ] **Step 4: Implement request accept/decline handlers and the 300-tick expiry sweep.** Both acceptances create a session and send its initial snapshot; decline/expiry cancels and frees indexes.
- [ ] **Step 5: Implement offer movement through server inventory operations.** Deposit copies into the sender-owned 18-slot escrow, increment the offer revision, clear both readiness flags, and broadcast a sanitized session snapshot.
- [ ] **Step 6: Implement ready/cancel handlers and the server tick sweep.** At every tick validate login, death, dimension, range, state, and countdown deadline; transition to COMMITTING only after final revalidation and then invoke the ledger delivery service.
- [ ] **Step 7: Register disconnect, death, dimension-change, screen-close, shutdown, login-recovery, and server-stop callbacks.** Damage callbacks must not cancel.
- [ ] **Step 8: Run packet unit tests and trade GameTests covering request timeout, range, dimension, death, disconnect, duplicate packets, and no item loss.**
- [ ] **Step 9: Commit with `git commit -m "feat: add authoritative trade networking"`.**

### Task 7: Build the client request HUD and trade screen

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/client/TheEconomistClient.java`
- Create: `src/main/java/com/jedts/theeconomist/client/trade/TradeKeyBindings.java`
- Create: `src/main/java/com/jedts/theeconomist/client/trade/TradeRequestHud.java`
- Create: `src/main/java/com/jedts/theeconomist/client/trade/TradeScreen.java`
- Create: `src/main/java/com/jedts/theeconomist/client/trade/TradeClientState.java`
- Create/modify: `src/main/resources/assets/theeconomist/lang/en_us.json`
- Create: `src/main/resources/assets/theeconomist/textures/gui/trade_request.png`
- Create: `src/main/resources/assets/theeconomist/textures/gui/trade_screen.png`
- Create: `src/test/java/com/jedts/theeconomist/client/trade/TradeClientStateTest.java`

**Interfaces:**
- Consumes: sanitized request/session snapshots and server-derived countdown ticks from `TradeNetworking`.
- Produces: bottom-right layout-A HUD, default `R`/`X` rebindable keys, a bilateral 6x3 offer screen, and intent packets only.

- [ ] **Step 1: Write client-state tests for request acceptance ticks, fade expiry at 250 milliseconds, timeout, server countdown, and readiness reset after an offer revision.**
- [ ] **Step 2: Implement `TradeKeyBindings` in a dedicated category with `R` accept and `X` decline defaults.**
- [ ] **Step 3: Implement layout-A request HUD with two heads, names, two tick states, accept/decline hints, and a 250-millisecond fade timer.** Use shape/icon differences so ticks are not color-only.
- [ ] **Step 4: Implement `TradeScreen` with local 27+9 inventory, local editable 6x3 offer, remote read-only 6x3 offer, both heads/readiness indicators, Crown totals, Cancel/Ready controls, and server-snapshot countdown overlay.**
- [ ] **Step 5: Route every client interaction to intent packets; never mutate local authoritative inventory or assume completion.** Close the screen on server cancellation and show a short reason where available.
- [ ] **Step 6: Run client-state tests and manually verify GUI scales, head/tick visibility, key rebinding, remote-slot protection, countdown reset, and fade behavior in a development client.**
- [ ] **Step 7: Commit with `git commit -m "feat: add trade request HUD and screen"`.**

### Task 8: Complete module wiring, README, recovery verification, and release gates

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/TradeModule.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Modify: `src/main/java/com/jedts/theeconomist/TheEconomistMod.java`
- Modify: `README.md`
- Create: `src/gametest/java/com/jedts/theeconomist/EndToEndFoundationGameTest.java`

**Interfaces:**
- Consumes: completed CurrencyModule, CitizenModule, TradeRuntime, client registrations, and saved-data recovery.
- Produces: a buildable dedicated server/client, documented milestone behavior, and end-to-end acceptance evidence.

- [ ] **Step 1: Register `TradeModule` after `CitizenModule` and ensure repeated initialization cannot create duplicate handlers or registries.**
- [ ] **Step 2: Add end-to-end GameTests for two-player request acceptance, 18-slot offers, readiness countdown, successful Crown/item exchange, full-inventory overflow at the receiver's feet, cancellation return, and duplicate commit protection.**
- [ ] **Step 3: Update README with exact config path/schema/reload command, Citizen creation commands, Crown identifiers/values, trade controls, timeout/range/readiness/cancellation rules, overflow behavior, and a clear implemented-vs-planned population roadmap.**
- [ ] **Step 4: Run the complete verification suite:**

```powershell
./gradlew.bat clean test
./gradlew.bat runGameTest
./gradlew.bat build
```

Expected: all unit tests and GameTests pass, the remapped JAR builds, client-only classes are absent from dedicated-server loading, and no test is silently skipped.

- [ ] **Step 5: Run a dedicated-server smoke test with `eula=true`, summon a Citizen, give all three Crowns, run `/theeconomist reload`, and search logs for class-loading, missing-registry, packet, persistence, or recovery errors.**
- [ ] **Step 6: Perform restart verification.** Confirm Citizen UUID/name/appearance persistence, active escrow recovery, offline original-owner return, and idempotent COMMITTING recovery.
- [ ] **Step 7: Inspect `git diff --check`, `git status --short --branch`, and the final JAR contents.** Expected: only intentional source/resources/docs are present and the working tree is clean after the release commit.
- [ ] **Step 8: Commit final documentation and acceptance evidence with `git commit -m "docs: document Citizen and secure trade foundation"`.**

## Self-Review Checklist

- [ ] Every spec section maps to at least one task: module architecture (1/8), Citizen (3), currency (2), trade request/session/escrow (4/5/6), client behavior (7), errors/security/recovery (5/6/8), testing (all tasks), and README (2/3/8).
- [ ] Placeholder scan found no unresolved markers, deferred implementation language, or unexplained generic error-handling instructions.
- [ ] Types and names are consistent: `TradeRules`, `TradeRequestRegistry`, `TradeSession`, `TradeLedger`, `TradeNetworking`, `TradeRuntime`, `TradeRequestHud`, and `TradeScreen` are introduced before later tasks consume them.
- [ ] Review-focus inputs are each pinned to an explicit test task.
- [ ] No task authorizes mailbox behavior, survival Crown generation, Citizen jobs, natural spawning, or other out-of-scope simulation.
- [ ] The execution method must be selected after human review; implementation must not begin from this plan without that selection.
