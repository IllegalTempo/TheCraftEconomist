# Blueprint System Design

## Goal

Add a server-authoritative blueprint workflow for planning buildings without modifying the world during preview. The first milestone provides an Empty Blueprint item, a data model for planned structures, and a safe planning interaction. Later milestones connect saved blueprints to building contracts and citizen builders.

## Scope

### First milestone

- Register an `Empty Blueprint` item.
- Let a player enter planning mode with the item.
- Render a client-side blue/transparent preview.
- Keep preview blocks fake; no world blocks are changed.
- Allow cancel and confirmation input.
- Send confirmation to the server for validation.

### Later milestones

- Save validated blueprints as item/world data.
- Add JSON blueprint definitions for built-in structures.
- Generate bills of materials and reserve real items.
- Create building contracts referencing a blueprint and work boundary.
- Execute construction in stages and validate completion.

## Architecture

`BlueprintItem` owns player interaction and planning-mode entry. `BlueprintPlan` is an immutable, testable structure containing an origin, transform, dimensions, and relative block states. `BlueprintPreview` is client-only rendering state and never writes blocks. `BlueprintValidator` runs on the server and rejects protected, impossible, or out-of-bound placements. A future `BlueprintSavedData`/item component stores validated plans for restart-safe use.

The client sends only a proposed plan. The server is authoritative and must revalidate every position and block state before accepting it. Preview packets are bounded by maximum dimensions and block count to prevent oversized plans.

## Data flow

1. Player uses Empty Blueprint.
2. Client creates a local origin and preview transform.
3. Client renders ghost blocks tinted blue.
4. Player rotates, mirrors, confirms, or cancels.
5. Confirmation sends the plan to the server.
6. Server validates permissions, bounds, claims, protected blocks, and size limits.
7. Accepted plans are saved; rejected plans return a clear reason.

## Safety rules

- Preview never changes world state.
- Server rejects protected blocks, claimed land without permission, portals, containers, redstone, rare blocks, and entities unless a later rule explicitly authorizes them.
- A plan has a bounded maximum volume and block count.
- Saving is atomic: invalid plans are not partially stored.
- Blueprint IDs and saved plans are persistent and unique.

## Testing

- Unit-test plan transforms, bounds, block-count limits, and serialization.
- Unit-test validator rejection reasons.
- Test that cancel produces no world mutation.
- Test that invalid server submissions are rejected.
- Run the full Gradle test suite and build before committing.

