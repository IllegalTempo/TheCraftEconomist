# Blueprint System Design

## Goal

Add a client-authored blueprint workflow with three explicit item states. Design previews and saved designs are client-side, while the server validates any placement or contract submission. Later milestones connect planned blueprints to building contracts and citizen builders.

## Scope

### Blueprint states

- `EMPTY`: the item has no captured structure and can start a design session.
- `DESIGNED`: the player has built the structure in client-side planning mode and saved those blocks into the blueprint. The captured structure contains relative block positions and block states, but no world placement.
- `PLANNED`: a designed blueprint has been positioned at an exact world origin with rotation/mirror. It is ready to be submitted for server validation and a construction contract; the actual world blocks are still unchanged.

The valid transitions are `EMPTY -> DESIGNED -> PLANNED`. A player may return a designed or planned blueprint to empty only through an explicit discard/reset action.

### First milestone

- Register an `Empty Blueprint` item.
- Let an empty player-held blueprint activate a client-side design session with a small actionbar GUI.
- Make the player ghost-like and allow client-side flight within a 32-block radius.
- Render client-side blue/transparent fake blocks as the player builds the design.
- Capture the built fake blocks into client-side blueprint storage when the blueprint is right-clicked again.
- Let a designed blueprint enter placement mode and render its exact planned position.
- Validate and store the planned origin, rotation, mirror, and boundary without changing world blocks.

### Later milestones

- Save validated blueprints as item/world data.
- Add JSON blueprint definitions for built-in structures.
- Generate bills of materials and reserve real items.
- Create building contracts referencing a blueprint and work boundary.
- Execute construction in stages and validate completion.

## Architecture

`BlueprintItem` owns state-aware interaction. `BlueprintDesign` is an immutable, testable captured structure containing dimensions and relative block states. `BlueprintPlacement` stores the exact world origin and transform for a designed blueprint. `BlueprintPreview` and local blueprint storage are client-only and never write blocks. `BlueprintValidator` runs on the server when a placement or contract is submitted and rejects protected, impossible, or out-of-bound placements.

The client owns design-session blocks and saved blueprint data. The client sends only a proposed placement or contract plan; the server is authoritative for world changes and must revalidate every position and block state before accepting it. Payloads are bounded by maximum dimensions and block count.

## Data flow

1. Player uses an `EMPTY` blueprint; the client shows “Design Mode Activated” in a small actionbar GUI.
2. Client ghosts the player, enables flight, and limits movement to a 32-block radius.
3. Client renders fake blue blocks while the player builds the design.
4. Player right-clicks the blueprint again; the client captures and stores the design as `DESIGNED`, removes fake blocks, and exits ghost mode.
5. Player uses the `DESIGNED` blueprint; the client previews the transformed structure and lets the player move/rotate it.
6. Player right-clicks to confirm; the client stores the exact placement as `PLANNED` and sends a bounded proposal when a contract is created.

## Safety rules

- Design and placement previews never change world state and are cleaned up on cancel, disconnect, death, or leaving the 32-block radius.
- Client-side blueprint data is untrusted. The server rejects protected blocks, claimed land without permission, portals, containers, redstone, rare blocks, and entities unless a later rule explicitly authorizes them.
- A plan has a bounded maximum volume and block count.
- Saving is atomic: invalid plans are not partially stored.
- Blueprint IDs and saved plans are persistent and unique.

## Testing

- Unit-test state transitions, design capture, placement transforms, bounds, block-count limits, and serialization.
- Unit-test validator rejection reasons.
- Test that cancel produces no world mutation.
- Test that invalid server submissions are rejected.
- Run the full Gradle test suite and build before committing.
