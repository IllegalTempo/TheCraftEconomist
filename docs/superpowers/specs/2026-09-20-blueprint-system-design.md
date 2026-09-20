# Blueprint System Design

## Goal

Add a server-authoritative blueprint workflow with three explicit item states. The first milestone provides the state model and safe planning interaction. Later milestones connect planned blueprints to building contracts and citizen builders.

## Scope

### Blueprint states

- `EMPTY`: the item has no captured structure and can start a design session.
- `DESIGNED`: the player has built the structure in planning mode and saved those blocks into the blueprint. The captured structure contains relative block positions and block states, but no world placement.
- `PLANNED`: a designed blueprint has been positioned at an exact world origin with rotation/mirror and a validated placement boundary. It is ready to become a construction contract; the actual world blocks are still unchanged.

The valid transitions are `EMPTY -> DESIGNED -> PLANNED`. A player may return a designed or planned blueprint to empty only through an explicit discard/reset action.

### First milestone

- Register an `Empty Blueprint` item.
- Let an empty player-held blueprint enter design mode.
- Render client-side blue/transparent fake blocks as the player builds the design.
- Capture the built fake blocks into the item as a designed blueprint.
- Let a designed blueprint enter placement mode and render its exact planned position.
- Validate and store the planned origin, rotation, mirror, and boundary without changing world blocks.

### Later milestones

- Save validated blueprints as item/world data.
- Add JSON blueprint definitions for built-in structures.
- Generate bills of materials and reserve real items.
- Create building contracts referencing a blueprint and work boundary.
- Execute construction in stages and validate completion.

## Architecture

`BlueprintItem` owns state-aware interaction. `BlueprintDesign` is an immutable, testable captured structure containing dimensions and relative block states. `BlueprintPlacement` stores the exact world origin, transform, and boundary for a designed blueprint. `BlueprintPreview` is client-only rendering state and never writes blocks. `BlueprintValidator` runs on the server and rejects protected, impossible, or out-of-bound placements. Blueprint item data stores the state and captured/placement data for restart-safe use.

The client sends only a proposed plan. The server is authoritative and must revalidate every position and block state before accepting it. Preview packets are bounded by maximum dimensions and block count to prevent oversized plans.

## Data flow

1. Player uses an `EMPTY` blueprint to enter design mode.
2. Client renders fake blue blocks while the player builds the design.
3. Player saves the design; the server validates and stores it as `DESIGNED`.
4. Player uses the `DESIGNED` blueprint to position an exact world placement.
5. Client previews the transformed structure without changing blocks.
6. Player confirms; the server validates permissions, bounds, claims, protected blocks, and size limits.
7. The item becomes `PLANNED` and stores the exact placement; rejected plans return a clear reason.

## Safety rules

- Design and placement previews never change world state.
- Server rejects protected blocks, claimed land without permission, portals, containers, redstone, rare blocks, and entities unless a later rule explicitly authorizes them.
- A plan has a bounded maximum volume and block count.
- Saving is atomic: invalid plans are not partially stored.
- Blueprint IDs and saved plans are persistent and unique.

## Testing

- Unit-test state transitions, design capture, placement transforms, bounds, block-count limits, and serialization.
- Unit-test validator rejection reasons.
- Test that cancel produces no world mutation.
- Test that invalid server submissions are rejected.
- Run the full Gradle test suite and build before committing.
