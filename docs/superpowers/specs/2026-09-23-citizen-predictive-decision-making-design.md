# Citizen Predictive Decision Making Design

Date: 2026-09-23

## Objective

Add a small predictive decision core to the custom Citizen behavior system. A Citizen will compare the expected result of available ordinary actions with its current needs, prefer continuing a useful action, observe the real result, and reconsider when the prediction fails.

Farming is the first complete use of the system. A farmer should normally hoe a group of suitable blocks, plant a group of empty farmland, harvest a group of ripe crops, or gather a group of seeds without switching action after every block. The system must remain understandable, deterministic, bounded per tick, and reusable by later work such as household activity, trading, and other occupations.

## Intent and Success Criteria

The design applies the useful engineering ideas from predictive processing without attempting to simulate a biological brain. In this mod, a prediction is a testable statement that an action will reduce a known need at a known cost.

The milestone succeeds when:

- farming choices use predicted benefit and cost instead of only a fixed action ordering;
- a successful farming action creates a soft preference to perform that action again;
- the preference survives temporary interruption by another behavior;
- a complete bounded search with no matching target releases the preference;
- repeated unreachable or invalid targets reduce confidence and eventually release the preference;
- emergency, combat, sleep, and confusion behavior retain their current authority;
- a Citizen's information screen explains its current intention and decision status;
- the decision core contains no Minecraft world access and can be unit tested independently;
- existing farming, land ownership, animation, inventory, sleep, combat, and household rules continue to work.

## Scope

This milestone includes:

- a pure, reusable decision model for scoring candidates;
- runtime decision memory for the preferred action and recent outcome feedback;
- a bounded farming observation cycle around the Citizen's home;
- farming candidate scoring for `HARVEST`, `PLANT`, `GATHER_SEEDS`, and `HOE`;
- consecutive same-action work when that work remains valuable;
- explicit action outcomes and feedback;
- decision details in the existing Citizen information UI;
- unit tests and focused GameTests for consecutive work and interruption.

This milestone does not add machine learning, neural networks, long-term personality learning, persistent memories, new jobs, new needs, new farming blocks, or predictive selection among emergency behaviors. It does not alter land prices, ownership rules, challenge combat results, sleep times, household assignment, or generated houses.

## Selected Approach

### Hybrid scheduler and predictive chooser

The current `CitizenBehaviorController` remains the authority for high-level behavior selection. Its priority and preemption rules are well suited to hard constraints: leaving water, panic, avoiding monsters, combat, sleep, and nighttime confusion must be dependable and immediate.

Predictive choice is introduced inside discretionary work. `FarmerBehavior` continues to own the farming cadence, while `FarmerWorkService` observes the world and asks the pure decision core which farming action and target has the best expected result.

This avoids two failure modes:

- replacing every behavior priority at once could allow ordinary work to compete incorrectly with danger or sleep;
- adding only a fixed same-action bonus inside `FarmerWorkPlanner` would produce batching but would not provide reusable prediction, confidence, or outcome feedback.

The boundary allows later behaviors to construct candidates for the same decision engine without depending on farming classes.

## Decision Concepts

### Action key

`CitizenActionKey` is a stable value containing a domain and action name, for example:

- `farming/harvest`
- `farming/plant`
- `farming/gather_seeds`
- `farming/hoe`

It is a value object rather than an enum so later modules can add actions without editing a central switch. Both parts must be nonblank lowercase identifiers. The key is runtime data in this milestone.

### Candidate

`CitizenDecisionCandidate<T>` describes one available action against one target. It contains:

- the `CitizenActionKey`;
- a typed target value;
- expected need reduction;
- opportunity value from nearby similar work;
- walking cost;
- resource cost;
- risk cost;
- confidence that the action can be completed;
- a deterministic tie-break rank.

All score inputs are bounded nonnegative integers. Need reduction, confidence, walking cost, resource cost, risk cost, and failure penalty each use the inclusive range 0 through 100. Opportunity value uses 0 through 60. World inspection and Minecraft types remain outside the decision engine except for the adapter's target type.

### Utility

The engine calculates:

```text
utility = need reduction
        + opportunity value
        + continuation preference
        + confidence
        - walking cost
        - resource cost
        - risk cost
        - recent failure penalty
```

Impossible actions are filtered before scoring. The engine selects the highest utility. Equal scores use the candidate's stable action rank and then its stable target order, so identical observations always produce the same choice.

The score explains preference; it does not guarantee that the preferred action always wins. A sufficiently urgent or valuable alternative may cause a switch.

The initial farming tuning is explicit and centralized:

| Term | Value |
| --- | ---: |
| Harvest need reduction | 100 |
| Plant need reduction | 70 |
| Gather need reduction | `min(80, max(0, 8 - seed count) * 10)` |
| Hoe or claim need reduction | 45 |
| Same-action opportunity | `min(30, max(0, matching candidates - 1) * 5)` |
| Hydrated hoe opportunity | 20 |
| Hoe adjacent to owned farmland | 10 |
| Preferred-action continuation | 60 |
| Newly observed action confidence | 10 |
| Preferred-action confidence | memory confidence, from 0 through 20 |
| Walking cost | `min(32, floor(2 * block distance))` |
| Plant resource cost | 4 |
| Hoe resource cost | 6 |
| Harvest or gather resource cost | 0 |
| Risk cost for current farming actions | 0 |
| Recent action failure penalty | `min(30, consecutive failures * 10)` |

The continuation term is applied by the engine when the candidate key equals memory's preferred key. Farming supplies action rank `HARVEST`, `PLANT`, `GATHER_SEEDS`, then `HOE`; coordinate order is Y, Z, then X. Constants live in the farming adapter rather than the reusable engine so later domains can use their own scale while respecting the candidate bounds.

### Decision memory

`CitizenDecisionMemory` is owned by the Citizen's `FarmerWorkService` for this milestone. It records:

- the preferred action key;
- confidence for that action;
- consecutive successes;
- consecutive failures;
- whether a complete observation cycle has found any valid preferred-action candidate;
- the latest outcome and short explanation.

A successful action makes its action key preferred, starts confidence at 10 for a new preference or increases it by 2 up to 20, clears consecutive failures, and increments consecutive successes. A failed action increments consecutive failures and lowers confidence by 1 for `TARGET_CHANGED`, by 5 for `UNREACHABLE`, `RESOURCE_MISSING`, or `INVENTORY_FULL`, and by 3 for `ACTION_FAILED`, never below zero. Three consecutive failures release the preference. A complete observation cycle that finds no valid candidate for the preferred action also releases it immediately. Releasing preference resets confidence, success count, and failure count to zero while retaining the latest explanation for the UI.

Emergency, combat, sleep, confusion, and return-home interruptions stop farming ticks but do not clear this memory. When farming resumes, the old preference receives a continuation benefit if it is still useful. A changed home, death, entity removal, or explicit work-service reset clears the memory.

Memory is runtime-only. Reloading a world starts a fresh decision cycle and requires no save migration.

### Outcomes

Every attempted work action reports one of these outcomes:

- `SUCCESS`: the expected world or inventory change occurred;
- `TARGET_CHANGED`: another actor or world update made the target invalid;
- `UNREACHABLE`: navigation could not begin or repeated movement made no progress;
- `RESOURCE_MISSING`: a required seed or usable hoe is absent;
- `INVENTORY_FULL`: expected drops cannot fit;
- `ACTION_FAILED`: the world mutation was rejected for another recoverable reason.

Only `SUCCESS` reinforces preference. Other results add failure feedback, clear the current target, and allow another observation. `TARGET_CHANGED` has a smaller penalty than `UNREACHABLE` because it says less about the action type itself. Exceptions continue through the existing behavior error handling rather than being converted into ordinary outcomes.

## Farming Perception Adapter

### Observation cycle

World access remains in `FarmerWorkService`. A focused `FarmerWorkScanner` extracts the existing radius-16 scan into a bounded component. It visits at most 128 positions per farming cycle and accumulates valid candidates until every offset in the radius has been visited once.

The scanner records candidates and summary counts for each action. Before a cached candidate is selected, the service validates its current block, ownership, inventory, and loaded-chunk conditions. Invalid cached candidates are discarded without touching the world.

When a preferred action exists, a newly observed valid candidate with that key may be selected as soon as the current bounded scan slice completes. If no preferred candidate is found, the scan continues across later farming cycles. The preference is released only after the entire radius has been checked. The accumulated alternatives are then scored, so the Citizen does not perform a lower-confidence switch merely because the first scan slice did not contain matching work.

When no preference exists, one complete scan establishes a consistent view before the first decision. The radius contains fewer than 805 offsets, so the current one-second farming cadence and 128-position budget finish the initial observation in at most seven farming cycles, or seven seconds, without scanning the whole radius in one server tick. The status communicates that the Citizen is surveying its farm.

### Farming beliefs and needs

The farming adapter derives its short-lived beliefs from authoritative state:

- seed count and usable hoe from `CitizenFarmInventory`;
- available inventory space through a trial insertion;
- owned and unclaimed farmland from `PlotClaimService`;
- crop maturity and block state from the server level;
- hydration through `NaturalWater`;
- distance from the Citizen's current position;
- adjacency to the Citizen's own farmland;
- recent action failures from decision memory.

Candidate need reduction follows the existing farming intent:

1. harvesting ripe owned wheat has the greatest base need reduction;
2. planting empty owned farmland has the next highest base value;
3. gathering seeds gains value while the inventory contains fewer than eight seeds;
4. claiming or hoeing suitable unclaimed ground has a lower base value but gains value near natural water and next to the Citizen's existing farmland.

The continuation benefit is large enough that a useful current action normally beats a small advantage from switching. It is still lower than the combined urgency and opportunity of a substantially better action. Similar candidates add a capped opportunity value, causing a visible group of same-type work to look more valuable than a single isolated task.

Walking cost rises with block distance and is capped, preserving the existing preference for nearby targets. Planting consumes a seed and hoeing consumes durability, so both include a small resource cost. Current farming has no general environmental danger model; risk cost is zero unless the adapter has real evidence. The field remains part of the reusable candidate contract for future behaviors such as land challenges.

### Existing physical rules

Prediction never authorizes an action. Immediately before execution, `FarmerWorkService` still enforces:

- the target is loaded and inside the home radius;
- the Citizen faces the target before interaction;
- crops and forage are broken through the existing drop and inventory path;
- animations and sounds use the current vanilla swing and block sounds;
- planting consumes a physical seed;
- hoeing requires and damages a physical hoe;
- farmland ownership is checked and recorded through `PlotClaimService`;
- connected farmland belonging to different owners remains separate;
- other owners' farmland is not modified;
- natural-water preference and the existing dry fallback remain effective;
- a land challenge only drives away the challenger and does not transfer ownership.

The predictive layer chooses among legal candidates. The execution service remains responsible for validating and performing mutations.

## Data Flow

Each farming work cycle follows this sequence:

1. `FarmerBehavior` confirms daytime, work age, home range, and cadence.
2. `FarmerWorkService` continues navigation or performs the selected target when one exists.
3. With no target, `FarmerWorkScanner` consumes its bounded slice and updates observations.
4. If a preferred-action candidate is available, the adapter builds decision candidates and asks `CitizenDecisionEngine` to score them.
5. If the preferred action is absent, scanning continues until exhaustion before alternatives are considered.
6. The selected target is revalidated and approached using the existing navigation and facing sequence.
7. Execution returns a `CitizenActionOutcome`.
8. `CitizenDecisionMemory` incorporates the outcome and exposes an explanation for the UI.
9. The next cycle continues the preferred action or begins a new observation cycle.

This produces feedback without allowing the scorer to mutate the world.

## Information Screen

The existing active behavior remains `farmer`. Farming status gains an intention-oriented message, such as:

- `Surveying farm`
- `Hoeing farmland — continuing batch (4 successful)`
- `Planting wheat — preferred action`
- `Reconsidering work — no more farmland to hoe`
- `Reconsidering work — target unreachable`
- `Work paused: inventory full`

`CitizenInfoDetails` adds bounded fields for preferred action, confidence, consecutive successes, and latest decision explanation. The work page displays these close to the current farm status. Server-authored values are bounded in the payload and existing convenience constructors receive neutral defaults.

No internal numeric utility breakdown is sent to clients in this milestone. The concise explanation is enough to understand behavior without exposing tuning constants as a user interface contract.

## Failure and Recovery

- A target changes before execution: report `TARGET_CHANGED`, discard it, and continue scanning.
- Navigation refuses a path: report `UNREACHABLE`, penalize that target and action, and select another candidate.
- Navigation starts but reduces squared distance by less than 0.25 across 200 game ticks: stop navigation and report `UNREACHABLE` instead of retrying forever.
- Hoe breaks or seeds disappear: report `RESOURCE_MISSING`; release a preference that can no longer produce a legal candidate.
- Inventory cannot accept drops: report `INVENTORY_FULL`, stop destructive work, and show the existing paused status.
- Chunk unloads: discard that candidate without forcing the chunk to load.
- The farm changes during a higher-priority interruption: revalidate cached observations before reuse.
- The complete scan finds no preferred work: release preference, score accumulated alternatives, and explain the switch.
- No legal candidate exists: remain idle under `FarmerBehavior` with the existing specific paused or waiting reason.

Recently failed target positions are remembered only for the current observation cycle. This prevents immediate retry loops while allowing a changed world to make the position useful later.

## Extensibility

A future ordinary behavior can use the decision engine by:

1. defining stable action keys;
2. observing authoritative state and constructing candidates;
3. predicting bounded benefit and costs;
4. executing the selected legal action outside the engine;
5. returning an explicit outcome to decision memory.

The reusable core knows nothing about blocks, crops, inventory classes, navigation, prices, or Minecraft entities. Domain adapters own those facts. Hard-priority behavior remains in `CitizenBehaviorController` until a later design deliberately changes that policy.

## Compatibility and Persistence

All new intention, confidence, observations, failed targets, and outcome data are runtime-only. Existing Citizens and worlds load without migration. Existing packet decoding remains compatible inside the same mod version by updating server and client payload codecs together.

The `FarmerWorkPlanner` public behavior is replaced by or adapted to the new pure engine. Existing planner tests are migrated to assert utility behavior rather than fixed ordinal priority. No Minecraft `Goal` is reintroduced.

## Testing

### Pure unit tests

Decision-engine tests verify:

- the highest utility legal candidate wins;
- continuation preference usually preserves a useful action;
- a sufficiently better alternative can overcome the soft preference;
- equal utilities resolve deterministically;
- walking, resource, risk, confidence, and failure terms affect utility in the documented directions;
- score inputs reject negative or out-of-bound values.

Decision-memory tests verify:

- success establishes and reinforces preference;
- a temporary interruption leaves preference intact;
- three consecutive failures release preference;
- a complete scan with no preferred candidate releases preference;
- home reset clears preference and feedback;
- confidence remains within its bounds.

Farming-adapter tests verify:

- mature wheat, empty farmland, forage, claimable farmland, and hoeable ground produce the correct candidates;
- seed shortage increases gathering value;
- hydration, adjacency, and same-action opportunity improve hoeing value;
- impossible candidates are filtered before scoring;
- a full scan is required before abandoning an absent preferred action;
- failed positions are skipped for the remainder of the observation cycle.

Payload and screen-model tests verify bounded decision fields and neutral compatibility defaults.

### GameTests

Focused GameTests verify:

- with three legal hoe targets and one lower-utility alternative, the Citizen completes all three hoe actions before switching;
- with three empty owned farmland blocks and one lower-utility alternative, the Citizen completes all three plant actions before switching;
- exhausting the preferred action causes a different useful action to start;
- an unreachable preferred target does not cause an endless navigation loop;
- danger or sleep interrupts farming and valid preferred work resumes afterward;
- a changed target during interruption is revalidated and skipped;
- every successful break, hoe, plant, and harvest still uses the current physical inventory, ownership, facing, animation, and sound rules.

The final verification runs all JUnit tests, required GameTests, and the complete Gradle build.

## Documentation

The README will describe predictive choice as a runtime decision aid, list the scoring concepts, explain soft action preference, and show how a future behavior supplies candidates and outcomes. It will state that hard-priority safety and sleep behaviors remain under the behavior controller.
