# Blueprint Reimplementation Design

## Goal

Replace the existing interactive blueprint implementation with the workflow documented in `README.md`: a vulnerable, stationary body with a private flying design viewpoint; client-only translucent block-model previews; and server-authoritative transitions from Empty to Designed to Planned.

## Source of truth and scope

The blueprint section of `README.md` is the behavioral source of truth. This reimplementation covers the interactive Empty, Designed, and Planned blueprint workflow. It does not add construction contracts, builder AI, bills of materials, built-in JSON structures, mirroring controls, or server-side construction execution.

The existing item serialization format remains readable so previously saved Designed and Planned blueprint items continue to work. Blueprint payloads remain bounded by the existing maximum dimensions and block count, but design-mode soul movement is not restricted to the old 32-block radius.

## Lifecycle states

- `EMPTY`: the item has no captured structure and can begin a design session.
- `DESIGNED`: the item stores normalized relative block positions and exact block states, but no world location.
- `PLANNED`: the item stores a design plus its confirmed dimension, origin, rotation, and mirror flags.

The only normal transitions are `EMPTY -> DESIGNED -> PLANNED`. Both transitions are atomic and server-authoritative. A rejected save leaves the item Empty; a rejected placement leaves it Designed.

## Design mode

Right-clicking an Empty Blueprint starts a local design session.

The real player entity remains at its server-owned position, stays visible to other players, and remains vulnerable to damage and environmental hazards. The client replaces the player's movement input with inert input and attaches the camera to a client-only flying proxy. Mouse movement controls the proxy's view, and movement keys move only the proxy. No flight, invisibility, invulnerability, or position changes are applied to the real player.

While the session is active, using a block item adds its exact default or interaction-derived block state to a private draft. Targeting an existing fake block permits continued construction. If no face is targeted, the first block is placed on the grid a short distance in front of the soul viewpoint. Attacking a fake block removes it. These operations modify only the draft map; they never call world block mutation APIs.

Right-clicking the blueprint again saves the draft. An empty draft remains in design mode and displays an explanatory message. A non-empty draft is normalized to minimum-relative coordinates, preserving block IDs and block-state properties, and is sent to the server for validation. On acceptance, the server changes the held item to Designed and the client restores its original camera and input.

## Placement preview

Right-clicking a Designed Blueprint begins a client-only placement session from the normal player viewpoint. The preview origin follows the player's crosshair. The `R` key rotates the structure clockwise by 90 degrees, and persistent HUD text above the actionbar shows the available control.

The preview renders every saved block with its original model and resolved block state, a blue tint, and 50 percent opacity. Moving or rotating the preview rebuilds one transient render map, so previous positions never accumulate. Rendering never alters real client or server world blocks.

Right-clicking proposes the current dimension, origin, rotation, and mirror flags to the server. The server verifies the held item and lifecycle state, checks the dimension, validates the bounded design payload and block palette, and rejects occupied or protected destinations. Only an accepted proposal changes the item to Planned.

## Components

### Session lifecycle

A blueprint session controller owns the current mode, relevant blueprint identity, cleanup, and transition requests. It delegates camera movement, draft editing, rendering, and networking rather than directly implementing them.

It cancels and restores all local state when the user presses Escape, dies, disconnects, changes dimension, loses the relevant blueprint, or otherwise leaves a valid client world. Cleanup is idempotent so repeated lifecycle events are harmless.

### Detached camera and input

A client-only camera proxy stores the soul position and orientation. It is never added to or synchronized with the server world. The controller saves the original camera entity and player input, installs inert player input while designing, moves the proxy from key states each client tick, copies view rotation into the proxy, and restores the saved objects during cleanup.

### Draft editing and targeting

A draft model owns fake block positions and states. A targeting service raycasts from the active viewpoint against both real-world collision shapes and draft-block unit cubes. It returns an explicit add or remove target so editing behavior can be unit-tested without rendering or item mutation.

Draft normalization calculates the occupied minimum and maximum coordinates, translates every position to a non-negative relative coordinate, and produces an immutable `BlueprintDesign`.

### Ghost rendering

One renderer accepts a map of world positions to resolved block states and draws original block models on the translucent render layer with a blue 50-percent-alpha tint. Design mode supplies the draft map; placement mode supplies a freshly transformed map. The renderer has no world-write dependency.

### Input and HUD

The blueprint item delegates client interaction to the session controller, eliminating competing item callbacks that can start and immediately finish a session on the same click. Normal block-use and attack callbacks are intercepted only while designing. A registered `R` key mapping rotates only during placement. A lightweight HUD renderer displays the placement control hint above the actionbar.

### Networking and server authority

The client sends separate intent payloads for saving a design and confirming a placement. The server always reads the currently held blueprint, validates the expected source state, and uses the server's copy of a Designed blueprint when confirming placement. Client data is treated as untrusted and bounded before allocation or persistence.

The server returns success through normal item synchronization and clear player messages. Failure never partially writes item state.

## Error handling

- Empty drafts are not saved.
- Invalid dimensions, excessive block counts, duplicate coordinates, unknown or forbidden block IDs, and malformed state properties are rejected.
- Placement in the wrong dimension, into occupied or protected positions, or without the matching Designed blueprint is rejected.
- Losing the blueprint or leaving the world cancels the local session.
- Cleanup always restores camera and input, even when cancellation is triggered more than once.
- Rejected operations report a specific reason and preserve the prior lifecycle state.

## Testing and verification

Automated tests cover:

- draft addition, replacement, removal, and normalization;
- real-world and fake-block targeting;
- lifecycle transitions and idempotent cleanup decisions;
- 90-degree placement rotations and transformed positions;
- preservation of block IDs and state properties;
- item-data round trips for Designed and Planned states;
- server validation of lifecycle state, bounds, block palette, dimension, and occupied/protected destinations;
- rejected operations preserving the previous item state.

Implementation follows test-driven development: each behavior begins with a failing focused test, followed by the smallest production change that makes it pass. Completion requires the full Gradle test suite and build to pass.

Manual multiplayer verification covers:

1. Another player sees the real body remain stationary and can damage it while the designer flies the soul viewpoint.
2. Design blocks use their original models and states with blue 50-percent transparency and are invisible to other players.
3. Saving produces a Designed Blueprint without changing world blocks.
4. Placement follows the crosshair, `R` rotates it, old preview positions do not accumulate, and right-click creates a server-approved Planned Blueprint.
5. Escape, death, disconnect, dimension change, and losing the blueprint restore camera/input and remove previews.

