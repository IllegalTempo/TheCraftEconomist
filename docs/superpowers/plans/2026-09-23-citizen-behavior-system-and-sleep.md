# Citizen Behavior System and Sleep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all Minecraft Goal based Citizen AI with an extensible behavior controller and add household bed sleeping plus nighttime confusion for Citizens without a generated household.

**Architecture:** Each Citizen owns stateful behavior instances behind a small lifecycle contract. A controller selects and preempts one behavior at a time by priority, while behaviors use Minecraft navigation, look, jump, sleeping, and combat primitives directly. Sleep uses runtime bed reservations and deterministic bed discovery; durable Citizen data remains unchanged.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Fabric Loom 1.17.21, JUnit 5, Fabric GameTest.

**Spec:** `docs/superpowers/specs/2026-09-23-citizen-behavior-system-and-sleep-design.md`

## Global Constraints

- `CitizenEntity.registerGoals()` must not register Minecraft `Goal` or target goal instances.
- Behaviors may use navigation, look control, jump control, sleeping APIs, entity queries, and combat APIs directly.
- Exactly one managed behavior owns movement and block interaction at a time.
- Active behavior, paths, bed targets, timers, and reservations are runtime-only state.
- Existing Citizen NBT and household formats remain readable without migration.
- Sleeping hours are day-time ticks 12,542 through 23,459 inclusive.
- Household residents use any available bed each night; there is no permanent bed ownership.
- Children may sleep and react to danger but remain ineligible for work.
- The workspace currently has no Git repository. At each commit step, run `git rev-parse --is-inside-work-tree`; commit only if it succeeds, otherwise preserve the tested checkpoint without initializing Git.

## Review Focus

- A reserved bed removed during approach must be released and another bed considered after cooldown; Task 4 adds the GameTest.
- Removing or killing a Citizen must release every reservation owned by its UUID; Task 3 adds the unit test and Task 7 wires entity cleanup.
- Daylight reached while walking or sleeping must stop navigation, wake the Citizen, and release the bed; Task 4 adds both transition tests.
- A household ID whose ledger entry is missing must select `CONFUSED` at night and explain the missing data in status; Task 5 adds the GameTest.
- A behavior exception must run cleanup and permit another behavior on the next tick; Task 1 adds the controller unit test.

---

### Task 1: Behavior contract and deterministic controller

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorContext.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorStopReason.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorController.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorControllerTest.java`

**Interfaces:**
- Produces: `CitizenBehavior` lifecycle methods `id`, `priority`, `canStart`, `canContinue`, `start`, `tick`, `stop`, and `status`.
- Produces: `CitizenBehaviorContext.citizen()`, `level()`, `gameTime()`, `navigation()`, `home()`, and `householdId()`.
- Produces: `CitizenBehaviorController.tick(CitizenBehaviorContext)`, `activeId()`, `activeStatus()`, and `stopAll(context, reason)`.
- Produces: stop reasons `INELIGIBLE`, `PREEMPTED`, `ENTITY_REMOVED`, `ERROR`, and `SHUTDOWN`.

- [ ] **Step 1: Write controller tests that fail because the behavior API does not exist**

```java
@Test void higherPriorityBehaviorPreemptsAndStopsCurrentFirst() {
    var events = new ArrayList<String>();
    var low = behavior("low", 10, events);
    var high = behavior("high", 20, events);
    high.eligible = false;
    var controller = new CitizenBehaviorController(List.of(low, high));
    controller.tick(CONTEXT);
    high.eligible = true;
    controller.tick(CONTEXT);
    assertEquals(List.of("start low", "tick low", "stop low PREEMPTED", "start high", "tick high"), events);
}

@Test void behaviorExceptionCleansUpAndAllowsRecoveryNextTick() {
    var broken = throwingBehavior("broken", 20);
    var fallback = behavior("fallback", 10, new ArrayList<>());
    var controller = new CitizenBehaviorController(List.of(broken, fallback));
    controller.tick(CONTEXT);
    broken.eligible = false;
    controller.tick(CONTEXT);
    assertEquals("fallback", controller.activeId());
    assertEquals(CitizenBehaviorStopReason.ERROR, broken.lastStopReason);
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat test --tests '*CitizenBehaviorControllerTest'`

Expected: compilation fails because the four behavior framework types do not exist.

- [ ] **Step 3: Implement the lifecycle contract and controller**

```java
public interface CitizenBehavior {
    String id();
    int priority();
    boolean canStart(CitizenBehaviorContext context);
    default boolean canContinue(CitizenBehaviorContext context) { return canStart(context); }
    default void start(CitizenBehaviorContext context) { }
    void tick(CitizenBehaviorContext context);
    default void stop(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) { }
    String status(CitizenBehaviorContext context);
}

public interface CitizenBehaviorContext {
    CitizenEntity citizen();
    ServerLevel level();
    long gameTime();
    PathNavigation navigation();
    Optional<BlockPos> home();
    Optional<UUID> householdId();
}
```

Implement stable priority selection by sorting once by descending priority while preserving constructor list order. On every tick, evaluate higher-priority candidates before retaining the current behavior. Wrap lifecycle calls so an exception stops the active behavior with `ERROR`, clears it, logs the behavior ID, and returns without starting another behavior until the next tick.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `.\gradlew.bat test --tests '*CitizenBehaviorControllerTest'`

Expected: all controller tests pass with zero failures.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: add Citizen behavior controller`; otherwise continue without changing repository metadata.

### Task 2: Live context, registry, and behavior construction

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/LiveCitizenBehaviorContext.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistryTest.java`

**Interfaces:**
- Consumes: Task 1 behavior contract and controller.
- Produces: `CitizenBehaviorRegistry.createController()` from per-Citizen behavior suppliers.
- Produces: context accessors for entity, server level, game time, navigation, home, and household ID.

- [ ] **Step 1: Write the registry order test**

```java
@Test void registryCreatesFreshStatefulBehaviorsInRegistrationOrder() {
    var creations = new AtomicInteger();
    var registry = new CitizenBehaviorRegistry(List.of(
            () -> { creations.incrementAndGet(); return testBehavior("first", 20); },
            () -> { creations.incrementAndGet(); return testBehavior("second", 10); }));
    int afterValidation = creations.get();
    registry.createController();
    registry.createController();
    assertEquals(List.of("first", "second"), registry.behaviorIds());
    assertEquals(afterValidation + 4, creations.get());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat test --tests '*CitizenBehaviorRegistryTest'`

Expected: compilation fails because the registry and live context do not exist.

- [ ] **Step 3: Implement context and registry factories**

The live context must reject client levels and expose values from the supplied Citizen on demand rather than snapshotting position or time. The registry accepts immutable `List<Supplier<CitizenBehavior>>`, validates unique IDs by creating one validation instance per supplier, and creates fresh behavior instances for every controller. The production default registry is assembled only after the concrete behaviors exist in Task 6.

```java
public CitizenBehaviorController createController() {
    return new CitizenBehaviorController(factories.stream().map(Supplier::get).toList());
}
```

- [ ] **Step 4: Run focused controller and registry tests**

Run: `.\gradlew.bat test --tests '*CitizenBehaviorControllerTest' --tests '*CitizenBehaviorRegistryTest'`

Expected: all focused tests pass.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: register Citizen behaviors`; otherwise preserve the tested files.

### Task 3: Sleep schedule, bed selection, and reservations

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenSleepSchedule.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedCandidate.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedSelector.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedReservations.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedReservationRuntime.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenSleepScheduleTest.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedSelectorTest.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/sleep/CitizenBedReservationsTest.java`

**Interfaces:**
- Produces: `CitizenSleepSchedule.isSleepingTime(long dayTime)`.
- Produces: `CitizenBedSelector.select(BlockPos home, List<CitizenBedCandidate>, UUID citizenId, CitizenBedReservations)`.
- Produces: `reserve`, `release`, `releaseAll`, `isReservedByOther`, and `reservedBed` operations.

- [ ] **Step 1: Write boundary, ordering, collision, and cleanup tests**

```java
@ParameterizedTest
@CsvSource({"12541,false", "12542,true", "23459,true", "23460,false", "36542,true"})
void sleepingWindowUsesWrappedDayTime(long tick, boolean expected) {
    assertEquals(expected, CitizenSleepSchedule.isSleepingTime(tick));
}

@Test void selectorUsesNearestDeterministicAvailableBed() {
    UUID citizen = UUID.randomUUID();
    var beds = List.of(candidate(3, 64, 0), candidate(1, 64, 0), candidate(-1, 64, 0));
    assertEquals(new BlockPos(-1, 64, 0), CitizenBedSelector.select(BlockPos.ZERO, beds, citizen,
            new CitizenBedReservations()).orElseThrow());
}

@Test void releaseAllMakesDeadCitizensBedAvailable() {
    var reservations = new CitizenBedReservations();
    UUID first = UUID.randomUUID();
    BlockPos bed = new BlockPos(2, 64, 2);
    assertTrue(reservations.reserve(bed, first));
    reservations.releaseAll(first);
    assertTrue(reservations.reserve(bed, UUID.randomUUID()));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.\gradlew.bat test --tests '*CitizenSleepScheduleTest' --tests '*CitizenBedSelectorTest' --tests '*CitizenBedReservationsTest'`

Expected: compilation fails for the missing sleep support classes.

- [ ] **Step 3: Implement pure sleep support**

Normalize time with `Math.floorMod(dayTime, 24_000L)`. Sort candidates by squared distance from home, then X, Y, and Z. Reservations use immutable `BlockPos` keys and UUID values. `CitizenBedReservationRuntime` uses a weak map keyed by `ServerLevel` and clears it through the existing server stop lifecycle hook.

- [ ] **Step 4: Run focused sleep tests and verify GREEN**

Run: `.\gradlew.bat test --tests '*CitizenSleepScheduleTest' --tests '*CitizenBedSelectorTest' --tests '*CitizenBedReservationsTest'`

Expected: all sleep support tests pass.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: add Citizen bed reservations`; otherwise preserve the tested files.

### Task 4: Household sleep behavior

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/SleepBehavior.java`
- Modify: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenHouseGameTests.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`

**Interfaces:**
- Consumes: behavior lifecycle, live context, sleep schedule, selector, reservations, household ID, and home spot.
- Produces: active behavior `sleep` with statuses `Finding bed`, `Walking to bed`, `Sleeping`, and `Waiting for bed`.

- [ ] **Step 1: Add failing GameTests for distinct beds, daylight wake, and bed removal**

```java
@GameTest(padding = 32, timeoutTicks = 300)
public void householdResidentsUseDifferentBedsAndWakeAtDay(GameTestHelper helper) {
    var residents = populateHouseWithBedsAtNight(helper);
    helper.runAfterDelay(160, () -> {
        if (residents.stream().noneMatch(CitizenEntity::isSleeping))
            throw new AssertionError("no resident reached a bed");
        long distinctBeds = residents.stream().filter(CitizenEntity::isSleeping)
                .map(citizen -> citizen.getSleepingPos().orElseThrow()).distinct().count();
        long sleepers = residents.stream().filter(CitizenEntity::isSleeping).count();
        if (distinctBeds != sleepers) throw new AssertionError("residents shared a bed");
        helper.getLevel().setDayTime(1_000);
        helper.runAfterDelay(5, () -> {
            if (residents.stream().anyMatch(CitizenEntity::isSleeping))
                throw new AssertionError("resident remained asleep during day");
            helper.succeed();
        });
    });
}
```

Add a separate test that removes the reserved bed during approach, waits past the retry cooldown, and asserts either a different reserved bed or `Waiting for bed`; also assert the removed position is no longer reserved.

- [ ] **Step 2: Run GameTests and verify RED**

Run: `.\gradlew.bat runGameTest`

Expected: the new sleep tests fail because household residents never start sleeping.

- [ ] **Step 3: Implement `SleepBehavior`**

Scan a 12 block horizontal and 5 block vertical area around home for head-half beds. Filter unloaded, occupied, obstructed, and reserved candidates. Reserve before navigation. Move at speed `1.0`; when within squared distance `2.25`, stop navigation, face the bed, and call `startSleeping(bedPos)`. Retry failures after 40 ticks. `stop` must call `stopSleeping()` when needed, stop navigation, and release the reservation.

- [ ] **Step 4: Run all GameTests and verify GREEN**

Run: `.\gradlew.bat runGameTest`

Expected: all required GameTests pass, including distinct beds, waking, and bed removal.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: let household Citizens sleep`; otherwise preserve the tested files.

### Task 5: Nighttime confusion and missing household handling

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/night/ConfusedBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/SleepBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`
- Modify: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenHouseGameTests.java`

**Interfaces:**
- Consumes: sleep schedule, household ID, home spot, and household ledger.
- Produces: active behavior `confused` with status `No generated household` or `Household record missing`.

- [ ] **Step 1: Add failing homeless and missing-ledger GameTests**

```java
@GameTest(timeoutTicks = 80)
public void homelessCitizenIsConfusedAtNightAndResumesByDay(GameTestHelper helper) {
    CitizenEntity citizen = helper.spawn(CitizenEntities.CITIZEN, new BlockPos(2, 2, 2));
    helper.getLevel().setDayTime(13_000);
    helper.runAfterDelay(3, () -> {
        if (!citizen.activeBehaviorId().equals("confused"))
            throw new AssertionError("homeless Citizen did not become confused");
        helper.getLevel().setDayTime(1_000);
        helper.runAfterDelay(3, () -> {
            if (citizen.activeBehaviorId().equals("confused"))
                throw new AssertionError("confusion continued during day");
            helper.succeed();
        });
    });
}
```

Create a second Citizen with a household UUID and home but no matching ledger entry; assert `confused` and status `Household record missing`.

- [ ] **Step 2: Run GameTests and verify RED**

Run: `.\gradlew.bat runGameTest`

Expected: the new tests fail because active behavior reporting and confusion do not exist.

- [ ] **Step 3: Implement confusion eligibility and cleanup**

At night, `SleepBehavior` is eligible only when household data resolves. `ConfusedBehavior` is eligible when there is no household ID or its home has no ledger entry. On start and tick it stops navigation; if outside the home restriction it requests navigation back to home, otherwise it remains still. During day `canContinue` returns false.

- [ ] **Step 4: Run all GameTests and verify GREEN**

Run: `.\gradlew.bat runGameTest`

Expected: all required GameTests pass.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: add nighttime confusion`; otherwise preserve the tested files.

### Task 6: Migrate emergency, work, home, and ambient AI

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/EscapeWaterBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/PanicBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/AvoidMonsterBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/combat/CombatBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/FarmerBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/ContractBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/home/ReturnHomeBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/LookAtPlayerBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/WanderBehavior.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/IdleBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/gametest/java/com/jedts/theeconomist/gametest/CitizenFarmingTradeGameTests.java`

**Interfaces:**
- Consumes: registry and behavior contract from Tasks 1-2, existing `FarmerWorkService`, contract registry, navigation, look, jump, and attack primitives.
- Produces: `CitizenBehaviorRegistry.defaults()` with IDs ordered as `escape_water`, `panic`, `avoid_monster`, `combat`, `sleep`, `confused`, `farmer`, `contract`, `return_home`, `look_at_player`, `wander`, and `idle`.
- Produces: a Citizen whose server AI decisions are exclusively controlled by `CitizenBehaviorController`.

- [ ] **Step 1: Add failing migration tests**

Add GameTests that assert farming does not change blocks at night while sleep or confusion is active, resumes after day begins, danger changes the active behavior away from sleep, and the existing land challenge combat result remains unchanged. Add a reflection test in `CitizenEntitiesTest` that creates a Citizen and verifies both goal selectors contain no registered available goals after `registerGoals` has run.

- [ ] **Step 2: Run focused unit and GameTests and verify RED**

Run: `.\gradlew.bat test --tests '*CitizenEntitiesTest'` followed by `.\gradlew.bat runGameTest`

Expected: the empty-goal assertion and at least one behavior migration test fail.

- [ ] **Step 3: Implement migrated behaviors and simplify `CitizenEntity.tick()`**

Assemble `CitizenBehaviorRegistry.defaults()` from suppliers for all twelve concrete behaviors and assert its exact ID order in `CitizenBehaviorRegistryTest`. Create the controller in the Citizen constructor after required entity controls exist. Leave `registerGoals()` empty. On each server tick call the controller once with `LiveCitizenBehaviorContext`. Move the 20 tick farming cadence into `FarmerBehavior`, the 200 tick contract cadence into `ContractBehavior`, and remove their independent calls from `CitizenEntity.tick()`.

Emergency behaviors use direct primitives:

```java
// Escape water
if (entity.isInWater() || entity.isInLava()) entity.getJumpControl().jump();

// Combat
var target = entity.getTarget();
entity.getLookControl().setLookAt(target, 30.0f, 30.0f);
entity.getNavigation().moveTo(target, 1.1);
if (entity.isWithinMeleeAttackRange(target)) entity.doHurtTarget(level, target);
```

Avoidance and panic choose a point away from the threat and call navigation directly. Return-home activates outside the home restriction. Look, wander, and idle never become eligible at night when sleep or confusion is eligible.

- [ ] **Step 4: Run complete unit and GameTest suites**

Run: `.\gradlew.bat test` followed by `.\gradlew.bat runGameTest`

Expected: all JUnit tests and required GameTests pass.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `refactor: move Citizen AI to behavior controller`; otherwise preserve the tested files.

### Task 7: Entity lifecycle cleanup and active behavior information

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenOverview.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenInfoPayload.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenInfoScreenData.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/CitizenClientHooks.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/CitizenInfoScreen.java`
- Modify: `src/test/java/com/jedts/theeconomist/citizen/info/CitizenOverviewTest.java`
- Modify: `src/test/java/com/jedts/theeconomist/citizen/info/CitizenInfoScreenDataTest.java`

**Interfaces:**
- Consumes: controller `activeId`, `activeStatus`, and `stopAll`; reservation `releaseAll`.
- Produces: `CitizenEntity.activeBehaviorId()` and `activeBehaviorStatus()` plus packet fields bounded to 32 and 128 UTF characters.

- [ ] **Step 1: Add failing information and removal cleanup tests**

```java
@Test void behaviorDisplayValuesSurviveScreenDataConstruction() {
    CitizenInfoScreenData data = screenDataWithBehavior("sleep", "Walking to bed");
    assertEquals("sleep", data.activeBehavior());
    assertEquals("Walking to bed", data.behaviorStatus());
}
```

Extend the reservation unit test to invoke the entity-removal cleanup path and assert that all UUID reservations become available.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `.\gradlew.bat test --tests '*CitizenOverviewTest' --tests '*CitizenInfoScreenDataTest' --tests '*CitizenBedReservationsTest'`

Expected: compilation fails for missing behavior display accessors.

- [ ] **Step 3: Wire lifecycle and UI fields**

Call controller cleanup and reservation release from entity removal/death hooks without altering plot-claim death behavior. Add active behavior and status immediately after family information in overview serialization and in the same order during decoding. Preserve existing convenience constructors by defaulting to `idle` and an empty status. Render:

```java
y = line(graphics, "Behavior: " + data.activeBehavior(), x, y);
y = line(graphics, "Status: " + data.behaviorStatus(), x, y);
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `.\gradlew.bat test --tests '*CitizenOverviewTest' --tests '*CitizenInfoScreenDataTest' --tests '*CitizenBedReservationsTest'`

Expected: all focused tests pass.

- [ ] **Step 5: Record the tested checkpoint**

If Git is available, commit `feat: show Citizen behavior status`; otherwise preserve the tested files.

### Task 8: Documentation and final verification

**Files:**
- Modify: `README.md`
- Verify: `build/libs/theeconomist-0.1.0.jar`

**Interfaces:**
- Consumes: the complete behavior system.
- Produces: documented extension steps and a verified distributable JAR.

- [ ] **Step 1: Document how to add a behavior**

Add a concise README section showing the contract methods, registry factory insertion, priority rules, movement ownership, mandatory cleanup, and required unit plus GameTest coverage. Document household sleep, any-bed selection, and nighttime `CONFUSED` behavior.

- [ ] **Step 2: Run the complete unit suite**

Run: `.\gradlew.bat test`

Expected: zero failures and zero errors.

- [ ] **Step 3: Run the complete GameTest suite**

Run: `.\gradlew.bat runGameTest`

Expected: every required GameTest passes.

- [ ] **Step 4: Build the distributable mod**

Run: `.\gradlew.bat build`

Expected: `BUILD SUCCESSFUL` and `build/libs/theeconomist-0.1.0.jar` exists.

- [ ] **Step 5: Inspect reports and artifact**

Sum `tests`, `failures`, and `errors` from `build/test-results/test/TEST-*.xml`; inspect the latest GameTest log for the required pass count; calculate SHA-256 with `Get-FileHash build/libs/theeconomist-0.1.0.jar -Algorithm SHA256`.

- [ ] **Step 6: Record the final checkpoint**

If Git is available, commit `docs: explain Citizen behavior extensions`; otherwise report that commits were unavailable because the workspace is not a Git repository.
