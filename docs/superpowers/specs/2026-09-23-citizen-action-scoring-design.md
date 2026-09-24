# Citizen Action Scoring and Decision Panel

## Goal

Use one live, explainable scoring system to select every existing Citizen behavior, and show the evaluations behind that choice in a **Decision** tab on the right-click citizen panel. The selected action and the displayed decision must come from the same server-side evaluation.

## Current context

`CitizenBehaviorController` owns one active behavior and currently evaluates `canStart` before selecting by integer priority. The registered behaviors are escape water, panic, avoid monster, combat, sleep, confused, farmer, contract, return home, look at player, wander, and idle. Citizen stats already include hunger, energy, safety, morale, anger, ambition, thrift, bravery, sociability, and loyalty. The existing information panel is server-authoritative and has Overview and Work tabs.

The current codebase has an initial utility-score API whose default translates old priority values to 0–100. This design replaces that compatibility path: no behavior priority may affect eligibility, scoring, tie-breaking, or selection after migration.

## Scoring model

Every behavior evaluates the current context into a structured result containing eligibility, a 0–100 score, score factors, and a short live explanation. Factors are normalized to 0–1:

- **Urgency:** how pressing the need, threat, or opportunity is now.
- **Benefit:** expected improvement if the action succeeds.
- **Capability:** how well this Citizen can perform it with current skills, traits, health, and tools.
- **Opportunity:** availability and accessibility of the target or resources.
- **Cost:** expected time, travel, money, and consumed resources.
- **Risk:** chance and consequence of injury, failure, or loss.

The shared calculation is:

`score = clamp(100 × urgency × benefit × capability × opportunity − 20 × cost − 20 × risk, 0, 100)`

Costs and risks use the same 0–1 range and their penalties are shown in the factor breakdown. Emergency escape from water or lava remains an explicit override for immediate survival. Other actions compete by score. The controller keeps its active action unless a challenger is at least five points higher, preventing rapid switching. Stable registry order resolves exact score ties.

No random roll is used to decide between otherwise identical evaluations. All score inputs are based on current state. Actions that cannot start still report an ineligible evaluation and a concise reason, so the panel can show why they were not selected.

## Behavior inputs

Each existing action implements the same evaluation contract with inputs taken from relevant live state:

- **Escape water:** current immersion/lava danger; immediate override while eligible.
- **Panic:** recent injury, current health, attacker presence and distance, and safety.
- **Avoid monster:** nearest monster distance, health, safety, and bravery.
- **Combat:** valid target, relative health and armor, anger/bravery, and target distance.
- **Sleep:** sleep schedule, energy deficit, household/bed availability, and travel cost.
- **Confused:** night-time need to settle and the missing household/home condition; this is a fallback state rather than a productive action.
- **Farmer:** work eligibility, ambition, available farming targets/tools/seeds, inventory capacity, and travel cost.
- **Contract review:** work eligibility, current contract opportunity/deadline, ambition, and review interval.
- **Return home:** distance from home, safety, time/travel cost, and time of day.
- **Look at player:** nearby non-spectator player, distance, and sociability.
- **Wander:** low-priority leisure opportunity, morale, safety, time of day, and available movement space.
- **Idle:** fallback value when no more useful eligible action wins.

This change scores only actions that already exist. Hunger and other needs may affect relevant existing actions, but this work does not add new behaviors such as eating, socializing, or seeking shelter.

## Evaluation and selection flow

1. At each existing behavior decision point, the controller asks every registered behavior for one evaluation. Evaluation must not move entities, consume items, reserve beds, or otherwise perform the action.
2. The controller selects the eligible evaluation with the highest score, applying the emergency override and five-point switching margin.
3. The controller retains the complete, bounded set of evaluations from that selection pass, including ineligible results, selected state, active state, factor values, and explanation.
4. Existing lifecycle methods (`start`, `tick`, and `stop`) continue to execute the selected behavior. Any state needed by the action is acquired only after selection, with existing cleanup rules preserved.
5. The citizen information request includes the latest decision snapshot. The client displays it in a generic, scrollable Decision tab, sorted by score, with action ID/name, eligibility, score, selected/active markers, factor contributions, and ineligibility explanation. It does not maintain a second scoring implementation.

The snapshot is transient server state, not persisted to citizen saves. Bound the candidate count to the registered behavior count and bound each factor/reason string before network serialization.

## Data and interface changes

- Replace the priority/default-score decision path with a behavior evaluation contract and small immutable evaluation/factor records.
- Keep behavior identity stable and provide a readable display name separately from its registry ID.
- Extend controller output to expose its most recent decision snapshot.
- Extend the citizen info DTO and payload codec with a bounded list of action evaluations.
- Add a Decision page to `CitizenInfoScreen`; render its rows from payload values instead of hard-coded behavior-specific UI cases.
- Keep Overview and Work content and trade interaction intact.

## Failure handling

- A behavior evaluation that throws is logged, marked ineligible with score zero, and does not prevent other actions from being considered.
- Non-finite scores or factors are normalized safely and cannot outrank valid decisions.
- Network input sizes are bounded by behavior and factor limits; malformed or oversized decision data is rejected by the payload codec.
- If no ordinary action is eligible, idle remains the fallback when its own conditions permit it.
- Existing action start/tick/stop exception cleanup remains in place.

## Testing

- Pure scoring tests cover normalization, score clamping, factor penalties, and deterministic outcomes.
- Controller tests cover score ranking, eligibility, emergency override, stable ties, switching margin, evaluation failures, and agreement between selected behavior and the recorded selected row.
- Per-behavior tests cover score changes from the relevant live input and ineligibility explanations.
- Payload codec tests round-trip bounded evaluation snapshots and reject invalid sizes/values.
- Screen/data tests verify the Decision tab renders provided evaluations and selection state without depending on a fixed behavior list.
- Run the full project test suite and build when the local Fabric Loom dependency is available.

## Out of scope

- Adding new citizen behaviors for currently unserved needs.
- Persisting decision history across saves or creating analytics dashboards.
- Changing player/citizen trading, economy, or existing action execution rules beyond what is required to make selection correspond to the score.
- Rebalancing citizen stat decay or adding new persistent stats.
