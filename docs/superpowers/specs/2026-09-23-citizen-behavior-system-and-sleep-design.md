# Citizen Behavior System and Night Sleep Design

Date: 2026-09-23

## Objective

Replace the Citizen entity's Minecraft `Goal` based AI with a small, extensible behavior system owned by the mod. Use that system for all Citizen decisions and add household sleeping at night. Citizens without a generated household enter a deliberately minimal `CONFUSED` state during sleeping hours so later work can define that behavior without changing the scheduler.

## Scope

This milestone includes:

- a reusable behavior contract, context, registry, and controller;
- deterministic priority and preemption rules;
- migration of the Citizen's existing swimming, panic, monster avoidance, combat, farming, contract, home, looking, wandering, and idle decisions away from Minecraft goals;
- nighttime sleep in any available bed belonging to the Citizen's generated house;
- temporary bed reservations so residents do not select the same bed;
- nighttime confusion for Citizens without a generated household;
- active behavior and status in the Citizen information screen;
- unit and GameTest coverage for selection, cleanup, sleep, waking, reservations, confusion, and existing behavior regression.

This milestone does not add permanent bed ownership, dreams, family interactions while asleep, a detailed confused action, or reproduction.

## Constraints

- `CitizenEntity.registerGoals()` will not register any Minecraft `Goal` or target goal.
- Behaviors may use Minecraft navigation, look control, jump control, sleeping APIs, entity queries, and combat APIs. These are movement and entity primitives, not the Minecraft goal scheduler.
- Exactly one managed behavior owns Citizen movement and block interaction at a time.
- Existing saved Citizens remain loadable. Runtime behavior state and bed reservations are reconstructed rather than persisted.
- Existing household, identity, farming, trading, combat, and ownership rules remain authoritative.

## Architecture

### Behavior contract

`CitizenBehavior` is implemented by each action. Every Citizen receives its own behavior instances because behaviors such as sleeping and panic keep temporary state.

The contract provides:

- a stable behavior identifier;
- an integer priority;
- `canStart(context)`;
- `canContinue(context)`;
- `start(context)`;
- `tick(context)`;
- `stop(context, reason)`;
- a short status string for the information screen.

`stop` must release everything owned by the behavior: navigation, bed reservations, sleeping state, attack state, and temporary targets as applicable.

### Behavior context

`CitizenBehaviorContext` exposes the current `CitizenEntity`, `ServerLevel`, game time, navigation and look helpers, home and household information, and shared services. It prevents individual behaviors from depending on unrelated entity internals.

### Registration

`CitizenBehaviorRegistry` contains behavior factories in one visible list. Adding a future behavior requires implementing the contract and adding one factory registration. Stable registration order breaks equal-priority ties.

### Controller

`CitizenBehaviorController` owns the instantiated behaviors and the current behavior. Each server tick it:

1. builds the current context;
2. stops an active behavior that can no longer continue;
3. finds the highest-priority eligible behavior;
4. preempts the active behavior only when the candidate has higher priority;
5. starts the selected behavior once;
6. ticks only the selected behavior.

The controller exposes the active behavior identifier and status. A behavior exception is logged, the behavior is stopped with an error reason, and the controller may select another behavior on the next tick. One broken behavior must not permanently disable the Citizen.

### Priority order

Priority descends in this order:

1. escape water;
2. panic after damage;
3. avoid nearby monsters;
4. combat the Citizen's currently assigned target, preserving the existing land-challenge combat path;
5. sleep or nighttime confusion;
6. farming and contract work;
7. return home;
8. look at nearby players;
9. wander;
10. idle observation.

Emergency behaviors can interrupt sleep. Nighttime sleep interrupts work, returning, looking, and wandering. Stable ordering prevents equal-priority behavior thrashing.

## Migrating Existing Citizen AI

`CitizenEntity.registerGoals()` becomes empty. The current `FloatGoal`, `PanicGoal`, `AvoidEntityGoal`, `MeleeAttackGoal`, `RandomStrollGoal`, `LookAtPlayerGoal`, and `RandomLookAroundGoal` registrations are removed.

The replacement behaviors call `PathNavigation`, `LookControl`, `JumpControl`, entity lookup, and attack methods directly. They preserve the current speeds and ranges unless tests show those values are incompatible with direct control.

The existing `FarmerWorkService` remains the farming implementation. `FarmerBehavior` owns its cadence and calls the service only while selected. Contract consideration runs through a work behavior rather than independently from `CitizenEntity.tick()`. This removes competing movement decisions from the entity tick method.

Children remain ineligible for work through `CitizenLifeStage.canWork()`, but may sleep, flee danger, look, wander, and return home.

## Nighttime Sleep

### Eligibility

Sleeping hours use Overworld day time modulo 24,000 ticks, from tick 12,542 through tick 23,459. A Citizen may start `SleepBehavior` when:

- it is sleeping time;
- the Citizen has a generated household ID and household home;
- no emergency behavior currently has priority.

Citizens do not sleep merely because a loose bed exists near a non-household home spot.

### Bed discovery

The home entrance is the search origin. `SleepBehavior` scans within 12 horizontal blocks and 5 vertical blocks, which covers the current 9 by 7 generated house. Candidates must:

- be the head half of a bed;
- be reachable in the loaded area;
- be unoccupied;
- have standing space suitable for approach;
- not be reserved by another Citizen.

Candidates are sorted by squared distance and then coordinates for deterministic selection. There is no permanent owner. A Citizen can use a different bed on another night.

### Reservations

`CitizenBedReservations` stores dimension-local, runtime-only mappings from bed position to Citizen UUID. A reservation is made before navigation begins and is valid only while:

- the Citizen still exists;
- the bed remains valid;
- `SleepBehavior` remains active.

Reservations are released on behavior stop, wake, death, entity removal, invalid bed, navigation failure, and server shutdown. This closes the race where two residents choose the same unoccupied bed while walking toward it.

### Sleeping and waking

The Citizen navigates to the reserved bed, faces it, and calls Minecraft's entity sleeping API when close enough. This reuses the vanilla pose and animation.

The behavior stops sleeping and releases the reservation when:

- sleeping hours end;
- the bed is removed or becomes invalid;
- the Citizen is hurt;
- a higher-priority water, panic, monster avoidance, or combat behavior becomes eligible.

If every household bed is occupied or reserved, the Citizen stays near home with status `WAITING_FOR_BED` and retries periodically without rapid path recalculation.

## Confused State

During sleeping hours, a Citizen with no generated household ID runs `ConfusedBehavior` instead of searching arbitrary beds. The behavior:

- stops work and ordinary wandering;
- stays within the existing home restriction when a home spot exists;
- clears navigation if it strays outside that restriction;
- reports active behavior `CONFUSED` with status `No household bed plan`.

It has no additional actions in this milestone. Daytime ends the state and normal eligible behaviors resume.

## Citizen Information Screen

The server overview payload adds:

- active behavior identifier;
- active behavior status.

The overview page displays both near family and work state. Existing farming status remains available on the work page. Packet strings are length bounded consistently with existing payload fields.

## Persistence and Compatibility

The active behavior, selected bed, path, retry timers, and reservations are runtime state and are not saved. After reload, the controller evaluates the Citizen and starts the correct behavior.

Existing persistent identity, gender, family role, life stage, aging, household, home, inventory, prices, stats, skills, and job data keep their current formats. No data migration is required for this behavior system.

If a Citizen reloads while lying in a bed, Minecraft sleeping state is allowed to load normally, and the controller reacquires the bed reservation on its first server tick or wakes the Citizen if the bed is invalid or sleeping hours have ended.

## Failure Handling

- No reachable household bed: report `WAITING_FOR_BED`, stay near home, and retry with a cooldown.
- Bed removed or claimed during approach: release it and select another after the cooldown.
- Navigation fails: release the reservation and retry later.
- Household data missing despite an ID: use `CONFUSED` during sleeping hours and report the missing household in status.
- Behavior throws: log its identifier, cleanly stop it, and allow selection on the next tick.
- Server or level stops: clear runtime reservation maps.

## Testing

Unit tests will verify:

- highest-priority eligible selection;
- stable ties;
- preemption calls stop before starting the replacement;
- an ineligible active behavior is cleaned up;
- exceptions do not permanently stall the controller;
- night window boundaries;
- deterministic bed candidate ordering;
- reservation exclusion and release;
- household versus homeless nighttime selection;
- packet and screen data preserve behavior identifier and status.

GameTests will verify:

- two household residents select different beds;
- a resident reaches a bed and enters vanilla sleeping state at night;
- daylight wakes a sleeping resident;
- bed removal releases and redirects the sleeper;
- danger can interrupt sleep;
- a Citizen without a generated household enters `CONFUSED` at night;
- farming pauses while sleep or confusion is active and resumes during daytime;
- generated household, family, farming, trading, and combat regression tests still pass.

The final gate is the full Gradle build, including all JUnit tests and required GameTests.
