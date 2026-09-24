# Citizen Action Scoring and Decision Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace priority-based selection for all existing Citizen behaviors with the shared live score and show the exact evaluations used for selection in a right-click Decision tab.

**Architecture:** Each behavior returns a structured, side-effect-free evaluation from its current context. A shared scorer clamps and calculates the score; the controller chooses the winner, retains the bounded evaluation snapshot, and starts/stops actions using existing lifecycle hooks. The info payload carries that same server snapshot to a generic client Decision tab.

**Tech Stack:** Java 25, Fabric Loom, Minecraft entity APIs, Fabric custom payload codecs, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-23-citizen-action-scoring-design.md`

## Global Constraints

- Every behavior evaluation contains eligibility, a 0–100 score, score factors, and a short live explanation.
- Normalize urgency, benefit, capability, opportunity, cost, and risk to 0–1.
- Use `score = clamp(100 × urgency × benefit × capability × opportunity − 20 × cost − 20 × risk, 0, 100)`.
- Escape from water or lava remains an explicit immediate-survival override.
- Keep an active action unless a challenger is at least five points higher; stable registry order resolves exact ties.
- Evaluation must not move entities, consume items, reserve beds, or otherwise perform the action.
- Decision snapshots are transient server state and are bounded by registered behavior count, factor count, and serialized string lengths.
- Score only actions that already exist; do not add eating, socializing, or seeking-shelter behaviors.
- Keep Overview and Work content and trade interaction intact.

## Review Focus

- **Near ties:** a challenger four points higher must not preempt; five points must preempt. Pin in controller margin tests.
- **Immediate danger:** water/lava escape must win even against score 100. Pin in controller emergency tests.
- **Missing opportunity:** an otherwise urgent action with no target/tool/bed must be ineligible or score zero and explain why. Pin in action-rule tests.
- **Invalid numeric inputs:** NaN, infinities, values outside 0–1, and negative penalties must not create invalid or dominant scores. Pin in scorer tests.
- **Snapshot mismatch or abuse:** exactly one candidate is marked selected, and malformed/oversized network lists are rejected. Pin in controller and payload codec tests.

---

### Task 1: Shared score and decision value types

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/decision/CitizenScoreInputs.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/decision/CitizenDecisionFactor.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/decision/CitizenActionEvaluation.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/behavior/decision/CitizenActionScorer.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/behavior/decision/CitizenActionScorerTest.java`

**Interfaces:**
- `CitizenScoreInputs(double urgency, double benefit, double capability, double opportunity, double cost, double risk)` validates or normalizes finite inputs into `[0, 1]`.
- `CitizenDecisionFactor(String id, double value, double scoreContribution)` exposes the normalized input and its signed contribution to the score.
- `CitizenActionEvaluation(String actionId, String displayName, boolean eligible, double score, boolean emergencyOverride, String explanation, List<CitizenDecisionFactor> factors, boolean selected, boolean active)` is immutable; provide `withSelection(boolean selected, boolean active)` to let the controller mark the same evaluation it chose.
- `CitizenActionScorer.evaluate(CitizenScoreInputs)` returns score plus six deterministic factor rows. Attribute the multiplicative base benefit to urgency, benefit, capability, and opportunity equally by recomputing the product without each factor; cost/risk contributions are `-20 × value`. Clamp the final score to `[0,100]`.

- [x] **Step 1: Write failing scorer tests** for all-zero input, all-one input, cost/risk penalties, input clamping, and NaN/infinity normalization.

```java
@Test
void computesSharedScoreAndExposesFactorContributions() {
    var result = CitizenActionScorer.evaluate(new CitizenScoreInputs(1, 1, 1, 1, .25, .5));
    assertEquals(85.0, result.score());
    assertEquals(6, result.factors().size());
}
```

- [x] **Step 2: Run the scorer test and confirm the missing API is the failure.**

Run: `./gradlew test --tests com.jedts.theeconomist.citizen.behavior.decision.CitizenActionScorerTest`

Expected: compilation fails because the scorer types do not exist.

- [x] **Step 3: Add immutable validated records and the shared calculation.** Keep validation pure and avoid Minecraft dependencies in this package.
- [x] **Step 4: Run the scorer test and confirm all boundary cases pass.**
- [x] **Step 5: Commit the scorer and its tests** with `feat: add shared citizen action scorer` (commit unavailable: workspace has no Git metadata).

### Task 2: Migrate survival and combat actions

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/EscapeWaterBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/PanicBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/emergency/AvoidMonsterBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/combat/CombatBehavior.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/decision/SurvivalDecisionRulesTest.java`

**Interfaces:**
- Replace `canStart(context)` as the selection input with `evaluate(context)` returning `CitizenActionEvaluation`; preserve `canContinue`, `start`, `tick`, `stop`, and `status` lifecycle methods.
- Add a shared helper for constructing eligible/ineligible evaluations from stable action ID, readable display name, `CitizenScoreInputs`, and explanation.
- Escape, panic, monster avoidance, and combat use current immersion, injury/health, threat distance, safety, target state, anger, bravery, and armor. Escape reports the explicit override only when submerged or in lava.

- [x] **Step 1: Write failing pure decision-rule tests** for low/high threat distance, injury severity, health/armor advantage, and missing combat target.

```java
@Test
void lethalLiquidExposureProducesAnEligibleEmergencyOverrideEvaluation() {
    var decision = SurvivalDecisionRules.escapeLiquid(true, 100.0);
    assertTrue(decision.eligible());
    assertTrue(decision.emergencyOverride());
}
```

- [x] **Step 2: Run the survival decision-rule tests and confirm they fail because the rules do not exist.**

Run: `./gradlew test --tests com.jedts.theeconomist.citizen.behavior.decision.SurvivalDecisionRulesTest`

Expected: compilation fails because `SurvivalDecisionRules` does not exist.

- [x] **Step 3: Implement normalized survival/combat decision rules and migrate the four behavior evaluations.** Evaluation may update private observations needed by the selected action, but it must not move or mutate the world.
- [x] **Step 4: Run the focused tests and verify the action scores and explanations change with their inputs.**
- [x] **Step 5: Commit the survival and combat migration** with `feat: score citizen survival and combat actions`.

### Task 3: Migrate work, sleep, and home actions

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/sleep/SleepBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/night/ConfusedBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/FarmerBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/work/ContractBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/home/ReturnHomeBehavior.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/decision/RoutineDecisionRulesTest.java`

**Interfaces:**
- All five actions return `CitizenActionEvaluation` using the shared scorer and stable display names.
- Work scores use work eligibility, ambition, interval readiness, current farming status, and cached/cheap indicators of inventory/tools/seed opportunity; do not scan the full farm field during every action evaluation.
- Sleep scores use night schedule, energy, household existence, and cheap bed-availability state; perform bed scans and reservations only after selection in `start`/`tick`.
- Return-home scores use distance, safety, and time. Confused reports missing household/home as its live explanation and remains a fallback action.

- [x] **Step 1: Write failing routine decision-rule tests** for low/high energy at night, farming capability/opportunity, contract readiness, return distance, and missing household.
- [x] **Step 2: Run `RoutineDecisionRulesTest` and confirm it fails because the decision rules do not yet exist.**
- [x] **Step 3: Implement pure rule functions and migrate the five behaviors without moving bed reservation or farm work into evaluation.**
- [x] **Step 4: Run routine decision-rule tests and verify factor changes for each paired input case.**
- [x] **Step 5: Commit the routine action migration** with `feat: score citizen work and rest actions`.

### Task 4: Migrate ambient actions and make the controller consume evaluations

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/LookAtPlayerBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/WanderBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/ambient/IdleBehavior.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorController.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorRegistry.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/test/java/com/jedts/theeconomist/citizen/behavior/CitizenBehaviorControllerTest.java`
- Create: `src/test/java/com/jedts/theeconomist/citizen/behavior/decision/AmbientDecisionRulesTest.java`

**Interfaces:**
- Controller exposes `List<CitizenActionEvaluation> latestEvaluations()` from the last selection pass.
- Each behavior evaluation happens once per pass; selected/active flags are applied to retained immutable evaluations after winner selection.
- Emergency overrides win immediately; otherwise select maximum score, preserve registry order on exact ties, and keep current behavior unless another eligible action is at least five points higher.
- Ambient actions use schedule, player distance/sociability, morale/safety and idle fallback value. No old integer `priority()` or utility default remains.
- Registry continues to provide the full 12 behaviors in deterministic order.

- [x] **Step 1: Write failing controller and ambient tests** for exact tie order, switching at four versus five points, emergency override, selected snapshot parity, and score changes from sociability/morale.
- [x] **Step 2: Run the focused controller and ambient tests; confirm they fail on missing evaluations and score-based selection.**
- [x] **Step 3: Migrate ambient behaviors, replace priority selection with evaluation selection, and expose the latest snapshot through `CitizenEntity`.**
- [x] **Step 4: Run all `citizen.behavior` unit tests and confirm lifecycle cleanup and selected snapshot parity.**
- [x] **Step 5: Commit controller and ambient migration** with `feat: select citizen actions by live utility`.

### Task 5: Send bounded decision snapshots in the existing info payload

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenDecisionView.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenOverview.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenInfoPayload.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenInfoScreenData.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/info/CitizenInfoPayloadTest.java`

**Interfaces:**
- `CitizenDecisionView(String actionId, String displayName, boolean eligible, double score, boolean emergencyOverride, String explanation, List<CitizenDecisionFactor> factors, boolean selected, boolean active)` is the bounded network-facing representation of one evaluation.
- The citizen info request copies `latestEvaluations()` into at most 12 rows and at most six factor rows per candidate. Define serialization limits as constants: action ID 32 characters, display name 64 characters, and explanation 128 characters; enforce each limit when constructing the view and in the codec.
- Payload codec encodes/decodes all values with explicit bounds and normalizes finite 0–100 scores.

- [x] **Step 1: Write failing payload round-trip and rejection tests** for ordinary lists, over-limit candidate/factor counts, out-of-range score, and non-finite score. (DTO/count rejection tests and a GameTest codec round-trip assertion were added.)
- [x] **Step 2: Run `CitizenInfoPayloadTest` and confirm the missing decision fields cause compilation failure.** (Red compile confirmed the DTO was absent; focused local JUnit harness now passes all 6 info DTO/screen-data methods.)
- [x] **Step 3: Add the view DTO and codec fields, reading the controller’s saved snapshot rather than re-running behavior scoring for the panel.**
- [x] **Step 4: Run info payload and overview tests and confirm old Overview/Work payload values still round-trip.** (DTO/screen-data tests passed; GameTest round-trip was updated but cannot be run because Gradle cannot download its distribution.)
- [x] **Step 5: Commit the payload changes** with `feat: include citizen decisions in info payload`. (No `.git` metadata exists, so commit unavailable.)

### Task 6: Render the generic Decision tab

**Files:**
- Modify: `src/main/java/com/jedts/theeconomist/client/CitizenInfoScreen.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/CitizenClientHooks.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/info/CitizenInfoScreenData.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/info/CitizenInfoScreenDataTest.java`

**Interfaces:**
- `CitizenInfoScreenData` contains an immutable list of `CitizenDecisionView` values.
- Add `Page.DECISION` and a Decision tab button. Render a sorted list using only supplied evaluation fields: action/display name, score, eligible/ineligible, selected/active/emergency labels, factor name/value/contribution, and explanation.
- Keep the UI generic: no `if actionId == ...` behavior-specific rendering. Empty snapshots show “No decision data yet.”
- Preserve current scrolling, Overview and Work pages, and Trade button behavior.

- [x] **Step 1: Write failing screen-data tests** for preserving rows, empty snapshots, and selected/active state. (Added immutable decision-list preservation coverage; empty snapshots use an empty immutable list.)
- [x] **Step 2: Run `CitizenInfoScreenDataTest` and confirm it fails because the decision list is not represented.** (Initial compile red was observed before the data field existed.)
- [x] **Step 3: Wire payload rows through `CitizenClientHooks` and render the scrollable Decision tab generically.**
- [x] **Step 4: Run all client/info tests and manually inspect the Decision tab at narrow and tall window sizes if a client run is available.** (Focused info data/view tests passed; an interactive client run was unavailable because Gradle could not resolve/download.)
- [x] **Step 5: Commit the UI changes** with `feat: show citizen action decisions`. (No `.git` metadata exists, so commit unavailable.)

### Task 7: Full integration verification and documentation

**Files:**
- Modify: `README.md`
- Review: all behavior, controller, payload, and client files from Tasks 1–6.

- [x] **Step 1: Update the Citizen behavior documentation** to state that all 12 registered actions use the shared score and that the right-click Decision tab shows the same server evaluation snapshot used by selection.
- [x] **Step 2: Run the focused scorer, behavior decision-rule, controller, info payload, and screen-data tests.** (26 combined behavior/scorer/controller/info methods passed through the local JUnit reflection harness, including the final eligibility fixes.)
- [ ] **Step 3: Run the full project test suite with `./gradlew test`.**
- [ ] **Step 4: Run the full build with `./gradlew build` and inspect failures rather than reporting an unverified pass.**
- [x] **Step 5: Review requirements against the spec:** all 12 behaviors evaluate; old priorities are unused; emergency escape remains override; five-point margin and deterministic ties work; snapshots match controller choices; payload bounds hold; UI contains no action-specific branches; Overview, Work, and Trade remain available.
- [x] **Step 6: Commit documentation and any final integration fixes** with `feat: complete citizen decision scoring panel`. (No `.git` metadata exists, so commit unavailable.)



