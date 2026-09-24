# Citizen Family Target Search and Memory Design

Date: 2026-09-23

## Objective

Give Citizens a shared, persistent way to search for spatial targets. A Citizen should walk around its home out to a 256-block horizontal radius, remember successful target locations with its family, and use those locations the next time the action needs a target.

## Scope

The shared system applies to behaviors that must discover a target in the world, including block targets such as seeds, farm plots, and beds, and entity targets such as creatures or threats. Each action supplies its own target finder, validity rules, and response movement. The target system owns movement through the search area, search progress, and target-location memory; behavior scoring and the action itself remain with the existing behavior.

The system is available to any target-seeking behavior, rather than being specific to farming. Actions that already have a fixed destination can keep using that destination directly.

## Approved requirements

- Search for targets and perform target actions within a 256-block horizontal radius of the Citizen's home anchor.
- Walk through the search area incrementally instead of scanning the whole radius synchronously.
- Save each Citizen's own search progress so it can continue after save and reload.
- Share the last-known target location for each action among members of the same family.
- Store locations only. Entity targets do not retain an entity UUID or reference.
- Search the Citizen's current local scan area first. Use a remembered family location only when no valid target is found locally. If that action cannot find a valid target there, erase that action's entry from the family memory (or the Citizen fallback) and continue the Citizen's search.
- Citizens without a household use their own saved target memory.
- Do not force-load chunks as part of searching.

## Architecture

### Shared target-search service

Add a reusable server-side target-search service that accepts an action key and an action-specific target finder. The finder inspects the current search area and reports a usable location for its action. For block actions it checks the relevant blocks. For entity actions it queries eligible entities and returns only their block location; entity identity is not part of saved memory.

The service supplies a consistent search origin, radius check, waypoint progression, and target-memory lookup for every consumer. It checks the Citizen's immediate local scan area first, then validates a remembered location, then continues the saved route. Each action remains responsible for eligibility, target validation, interaction, and movement after a target is found. An action that works on a target walks to it; an emergency action responds according to its purpose, such as moving away from a threat once the threat is found. Existing behavior priority and scoring do not change.

### Walking search route

The service advances through small, reachable waypoints in an expanding route around home. At each waypoint, the action-specific finder checks nearby loaded terrain or entities. Search is incremental and bounded per service tick. Search legs stay local so Citizens can load terrain naturally by walking; the service never requests a chunk load solely to inspect it. When it finds a target, the behavior chooses how to respond: work actions navigate to the target and act there if their other requirements are met, while emergency actions such as panic or avoiding monsters move away from a confirmed threat. If an emergency has a remembered threat location, it may visit that location to check it, then immediately applies its flee response when the threat is present.

Waypoints and actionable targets must remain within the 256-block horizontal radius from the home anchor. This radius governs discovery and, for work actions, travel to a target; it replaces their narrower home-work radius. Emergency response movement after locating a threat is not redirected toward that threat. Unreachable waypoints are skipped so a blocked route cannot trap the search. After covering the radius, the route wraps and continues searching. Search progress is maintained separately for each Citizen and action so one family member does not overwrite another member's route cursor.

### Family target memory

Extend each household entry in `HouseholdSavedData` with an optional map from stable target-action key to last-known `BlockPos`. Distinct targets within a behavior use distinct keys; for example, farmer hoeing, planting, and harvesting each have separate locations. Household data is already dimension-scoped and codec-backed. The new field defaults to an empty map so existing world saves remain readable. Updating or clearing an entry marks the saved data dirty.

When a Citizen has no household record, the same action-keyed location map is kept in that Citizen's entity save data. Family memory is shared knowledge, not a reservation: existing farmland claims, bed reservations, and other ownership rules continue to decide who may use a target.

### Target use and invalidation

When an action needs a target, it first searches the Citizen's current local scan area. If that finds nothing, it asks the family memory (or the Citizen fallback memory) for its saved location. The search service visits that location and asks the action's finder to validate the target there. A valid target is returned to the action and the remembered location is refreshed. If no valid target is present at the remembered location, the location is erased from the owning memory store (family or Citizen fallback) and the Citizen resumes its own saved waypoint route. For a work target, the action then travels to the target and acts. For an emergency target, the action follows its emergency response movement after validation instead of being forced to path toward the target.

Entity memories follow the same location rule as block memories. The finder looks for a currently valid entity target at the saved location; it does not follow an entity that moved elsewhere. If none is present there, the location is erased and a new search continues.

### Initial consumers

The first integration covers existing actions that discover world targets:

- `SeedFindingBehavior` searches for seed forage.
- `FarmerBehavior` searches for workable soil, hydrated plots, and ripe crops.
- `SleepBehavior` searches for usable beds.
- `LookAtCreatureBehavior` searches for living creature targets and uses its normal look response.
- `CombatBehavior` searches for its action-eligible challenger target and keeps its existing combat response.
- `AvoidMonsterBehavior` and `PanicBehavior` use the saved-location interface for their threat targets and retain their response movement away from threats.

The framework remains usable by future target-seeking behaviors without adding another search route or memory store.

## Data and failure handling

- Search cursors and family target maps are keyed by stable target-action IDs, not display labels. Each key identifies one type of target that the action can actually use.
- Search state is dimension-scoped. A state from another dimension or outside the current home radius is discarded.
- Before action, a consumer rechecks its target; changed blocks, removed entities, invalid beds, and newly claimed plots cannot be acted on using stale memory.
- Clearing a stale target affects only that action's family entry. Other remembered action locations remain intact.
- Navigation failure advances the Citizen's search route rather than repeating the same waypoint forever.
- If no target is found in a complete sweep, searching starts another sweep while the behavior remains eligible.

## Verification approach

Implementation verification should cover bounded waypoint travel and work, emergency movement away from a located threat, incremental search without forced chunk loading, resuming a Citizen's cursor after save and reload, sharing one action location among household members, the no-household fallback, and stale block/entity location invalidation followed by continued search. Existing farm claims, bed reservations, action priorities, tool requirements, and inventory requirements remain enforced at targets anywhere within the 256-block radius.

## Out of scope

The system does not save live entity references, reserve targets for a family member, change behavior scores, or force-load chunks. Searching to 256 blocks does not automatically make every target usable if the action's existing eligibility, tool, inventory, time, or ownership requirements are not met.
