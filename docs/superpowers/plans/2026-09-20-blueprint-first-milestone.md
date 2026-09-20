# Blueprint First Milestone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an `Empty Blueprint` item with persistent `EMPTY`, `DESIGNED`, and `PLANNED` states, fake client previews, and server validation without placing real world blocks.

**Architecture:** Keep blueprint domain data in pure records so state transitions, transforms, bounds, and serialization are unit-testable. Store state on the item stack using Minecraft components/NBT, while a client-only preview controller renders ghost blocks. The server receives proposed designs/placements, validates limits and protected targets, and only then mutates the blueprint item state.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric API 0.161.0+26.3, JUnit 5, Minecraft `ItemStack` data components, Fabric play networking, `GuiGraphicsExtractor`/client rendering.

**Spec:** `docs/superpowers/specs/2026-09-20-blueprint-system-design.md`

## Global Constraints

- Preview blocks must never modify world state.
- Valid state transitions are exactly `EMPTY -> DESIGNED -> PLANNED`; reset is explicit.
- The server is authoritative and revalidates every proposed plan.
- Reject protected blocks, claimed land without permission, portals, containers, redstone, rare blocks, and entities.
- Enforce bounded dimensions and block count before allocating or storing a plan.
- Run `./gradlew test` and `./gradlew build` before completion.

## Review Focus

- Empty blueprint used without a valid target: it enters design mode without changing the world. Test in Task 2.
- Oversized or malformed design packet: server rejects it before allocation. Test in Task 3.
- Rotation/mirror changes: relative block positions transform deterministically. Test in Task 1.
- Designed blueprint placed over protected blocks: placement is rejected and remains `DESIGNED`. Test in Task 3.
- Restart/serialization: all three states and block states round-trip without loss. Test in Task 1.

### Task 1: Blueprint domain model and item-state serialization

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintState.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintBlock.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintDesign.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintPlacement.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintLimits.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintStackData.java`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintDesignTest.java`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintStackDataTest.java`

**Interfaces:**
- `BlueprintState` exposes `EMPTY`, `DESIGNED`, `PLANNED`.
- `BlueprintDesign` exposes dimensions, immutable relative blocks, `rotated(int quarterTurns)`, `mirrored(boolean x, boolean z)`, and block-count validation.
- `BlueprintPlacement` exposes exact origin, rotation, mirror, and transformed world positions.
- `BlueprintStackData` reads/writes the state and design/placement payload on an `ItemStack` without depending on client classes.

- [ ] **Step 1: Write failing tests** for state transitions, transform coordinates, size limits, and round-trip serialization.
- [ ] **Step 2: Run `./gradlew test --tests '*Blueprint*'` and verify the new tests fail because the domain types do not exist.
- [ ] **Step 3: Implement the immutable records and explicit transition methods; reject invalid dimensions, duplicate positions, empty block IDs, and oversized plans.
- [ ] **Step 4: Implement item-stack serialization using the project’s Minecraft 26.3 value/component APIs, preserving state, block states, origin, and transform.
- [ ] **Step 5: Run the focused tests, then the full test suite.
- [ ] **Step 6: Commit with `feat: add blueprint domain state model`.

### Task 2: Empty Blueprint item and client planning controller

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintItems.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/EmptyBlueprintItem.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintClientController.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`
- Create/modify: `src/main/resources/assets/theeconomist/lang/en_us.json`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/EmptyBlueprintItemTest.java`

**Interfaces:**
- `BlueprintItems.EMPTY_BLUEPRINT` is the registered item.
- `EmptyBlueprintItem.use(...)` starts design mode for `EMPTY`, starts placement mode for `DESIGNED`, and gives a clear message for `PLANNED`.
- `BlueprintClientController` owns only local preview state and exposes cancel/confirm/rotate/mirror actions.

- [ ] **Step 1: Write failing item tests** proving `EMPTY` enters design mode, `DESIGNED` enters placement mode, and `PLANNED` cannot be reused as an unplanned design.
- [ ] **Step 2: Run the focused test and verify the expected missing-item failure.
- [ ] **Step 3: Register the item using the existing `CrownItems` registration pattern and add its display name/tooltip.
- [ ] **Step 4: Implement controller input bindings: confirm, cancel, rotate, and mirror; render fake blue blocks only through client rendering hooks.
- [ ] **Step 5: Ensure cancel clears only controller state and never changes the item or world.
- [ ] **Step 6: Run all tests and build; commit with `feat: add empty blueprint planning mode`.

### Task 3: Server packets, validation, and state transitions

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintValidator.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintPackets.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintServerHandlers.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java` or add `BlueprintModule.java` and register it in `ModModules.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/TheEconomistClient.java`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintValidatorTest.java`

**Interfaces:**
- `BlueprintValidator.validateDesign(...)` returns an immutable success/rejection result.
- `BlueprintValidator.validatePlacement(...)` checks transformed positions against world permissions and protected block rules.
- A design confirmation packet changes `EMPTY` to `DESIGNED` only after server validation.
- A placement confirmation packet changes `DESIGNED` to `PLANNED` only after server validation.

- [ ] **Step 1: Write failing validator tests** for valid plans, size overflow, protected blocks, claimed/unauthorized positions, and out-of-bounds placement.
- [ ] **Step 2: Run focused validator tests and verify they fail for missing validation behavior.
- [ ] **Step 3: Implement pure size/state validation first, then server-world checks with explicit rejection reasons.
- [ ] **Step 4: Register bounded C2S packets and handlers; reject packets whose player does not hold the referenced blueprint stack.
- [ ] **Step 5: Apply state transitions atomically: invalid requests leave the stack unchanged; valid requests update the held stack and send a success message.
- [ ] **Step 6: Run the full test suite and build; commit with `feat: validate and save planned blueprints`.

### Task 4: Integration verification

**Files:**
- Modify: `README.md` with the implemented blueprint states and controls.
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintIntegrationTest.java` if a server test harness is available; otherwise document the manual scenario in the plan completion notes.

- [ ] **Step 1: Run `./gradlew test`.
- [ ] **Step 2: Run `./gradlew build`.
- [ ] **Step 3: Manually verify: empty item → fake design preview → save as designed → exact placement preview → reject protected placement → accept safe placement as planned.
- [ ] **Step 4: Remove generated `logs/latest.log`, inspect `git diff`, and commit documentation with `docs: document blueprint controls`.

