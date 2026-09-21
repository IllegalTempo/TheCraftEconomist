# Two-Corner Blueprint Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace flying design mode with two right-clicked world corners that produce a server-captured Designed Blueprint, including block-entity data.

**Architecture:** The client sends only corner-selection intents and renders server-confirmed selection feedback. A server selection manager validates the held item and both clicks, then a bounded capture service reads the world and writes a versioned blueprint item. The existing Designed-to-Planned transition and previews continue to consume `BlueprintDesign`.

**Tech Stack:** Minecraft 26.3, Fabric Loader/API, Java 25, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-21-two-corner-blueprint-capture-design.md`

## Global Constraints

- Preserve the existing state path `EMPTY -> DESIGNED -> PLANNED`; failed capture leaves the item Empty.
- Capture non-air blocks, exact block states, and available block-entity data; exclude air and entities.
- Keep captured data server-authored, bounded, and inaccessible where permission checks reject access.
- Keep existing placement rotation, occupancy checks, planned preview, and legacy blueprint items working.
- Do not materialize blocks or items during capture or planning.
- Preserve unrelated dirty working-tree edits; in particular, planned-preview edits are not throwaway design-mode edits.

## Review Focus

1. Clicking a chest corner must select it without opening the real chest; Task 4 adds an input-routing regression test and manual check.
2. Swapping a different Empty Blueprint into the same slot must not reuse a selection; Task 3 tests exact stack identity.
3. An oversized chest inventory must reject the whole capture, not truncate contents; Task 2 tests byte caps.
4. A legacy Designed/Planned item must decode with no block-entity payload; Task 1 tests legacy tags.
5. A changed container payload must invalidate a placement fingerprint; Task 1 tests payload-sensitive hashing.

## File map

- `blueprint/BlueprintBlock.java`, `BlueprintDesign.java`, `BlueprintStackData.java`, `BlueprintDesignFingerprint.java`: immutable captured-data model, compatible item codec, stale-preview identity.
- `blueprint/BlueprintCapture.java`, `BlueprintCaptureSource.java`, `ServerBlueprintCaptureSource.java`: inclusive cuboid geometry, limits, block/state/entity snapshot, server-world access checks.
- `blueprint/BlueprintCaptureSelection.java`, `BlueprintCaptureCornerPayload.java`, `BlueprintCaptureFeedbackPayload.java`: server-owned two-click lifecycle and minimal networking.
- `blueprint/BlueprintServerHandlers.java`, `BlueprintModule.java`, `BlueprintValidator.java`, `BlueprintTransitionService.java`: capture integration and retained placement validation.
- `client/BlueprintClientController.java`, `TheEconomistClient.java`, `BlueprintHudOverlay.java`: selection input, feedback, outline/HUD, and preserved placement/planned preview.
- Old design-only camera, mouse mixin, draft, and flight files: remove only after their usages and the user's dirty changes have been accounted for.
- `README.md`: replace the old design-mode instructions with the two-corner workflow and data limits.

---

### Task 1: Versioned block-entity data model

**Files:** Modify `BlueprintBlock.java`, `BlueprintDesign.java`, `BlueprintStackData.java`, `BlueprintDesignFingerprint.java`, and `BlueprintValidator.java`; test `BlueprintStackDataTest.java` and `BlueprintDesignFingerprintTest.java`.

**Interfaces:** Keep existing four-/five-argument `BlueprintBlock` constructors. Add optional `CompoundTag blockEntityData` with defensive copies and `BlueprintBlock.blockEntityData()` returning a copy or null. Existing designs decode with null payload. `BlueprintDesignFingerprint.of(BlueprintDesign)` includes canonical payload bytes.

- [ ] **Step 1: Write failing round-trip and fingerprint tests.**

```java
CompoundTag chest = new CompoundTag();
chest.putString("id", "minecraft:chest");
chest.putString("CustomName", "Supplies");
BlueprintBlock block = new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north", chest);
BlueprintDesign design = new BlueprintDesign(1, 1, 1, List.of(block));
assertEquals(design, BlueprintStackData.readTag(BlueprintStackData.writeTag(
        new BlueprintStackData(BlueprintState.DESIGNED, design, null))).design());
assertNotEquals(BlueprintDesignFingerprint.of(design), BlueprintDesignFingerprint.of(
        new BlueprintDesign(1, 1, 1, List.of(new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north")))));
```

Also construct a legacy `Blocks` tag with no entity data and assert it decodes with null payload. Test that mutating the input or returned tag cannot mutate a `BlueprintBlock`.

- [ ] **Step 2: Run `./gradlew.bat test --tests '*BlueprintStackDataTest' --tests '*BlueprintDesignFingerprintTest'` and confirm failure is the absent payload API/behavior.**
- [ ] **Step 3: Implement the smallest compatible model/codec/hash change.** Preserve old constructors; store a copied tag per block; write a `FormatVersion` only for the new representation; read old tags without it; validate that payload block-entity ID matches the block type when known. Canonical hashing recursively sorts compound keys, preserves list order and numeric types, and never relies on `CompoundTag.toString()` ordering.
- [ ] **Step 4: Run the focused tests and `./gradlew.bat test`; inspect failures.**
- [ ] **Step 5: Commit only Task 1 files with `git add -- <explicit paths>` and `git commit -m "feat: persist captured blueprint block-entity data"`.**

### Task 2: Bounded server-side region capture

**Files:** Create `BlueprintCapture.java`, `BlueprintCaptureSource.java`, `ServerBlueprintCaptureSource.java`; modify `BlueprintLimits.java` and `BlueprintValidator.java`; test `BlueprintCaptureTest.java`.

**Interfaces:** `BlueprintCapture.capture(BlockPos first, BlockPos second, BlueprintCaptureSource source)` returns `BlueprintCapture.Result(boolean accepted, BlueprintDesign design, String reason)`. `BlueprintCaptureSource` exposes `boolean loaded(BlockPos)`, `boolean accessible(BlockPos)`, `BlockState state(BlockPos)`, and nullable `CompoundTag blockEntityData(BlockPos)`. The server adapter wraps `ServerLevel` and `ServerPlayer`.

- [ ] **Step 1: Write failing tests using a small in-memory source.**

```java
BlueprintCapture.Result result = BlueprintCapture.capture(new BlockPos(2, 64, 2),
        new BlockPos(1, 64, 1), sourceWithStoneAndChest);
assertTrue(result.accepted());
assertEquals(2, result.design().width());
assertEquals(2, result.design().depth());
assertEquals("minecraft:chest", result.design().blocks().stream()
        .filter(b -> b.blockId().equals("minecraft:chest")).findFirst().orElseThrow().blockId());
```

Separate cases assert a one-block region works; all-air, unloaded, inaccessible, out-of-bounds, over-volume, and over-byte regions reject without a partial design. Include a chest tag with nested item components and verify the saved tag survives unchanged.

- [ ] **Step 2: Run `./gradlew.bat test --tests '*BlueprintCaptureTest'` and confirm the new capture API is missing.**
- [ ] **Step 3: Implement geometry and snapshotting.** Check each axis against 64 and volume against 4096 before iteration; reject a block-entity tag over 32 KiB or total encoded design over 256 KiB. Iterate the inclusive cuboid without force-loading chunks, skip air, encode each block's full state, and copy available block-entity data. The server adapter checks world bounds, loaded state, `mayInteract`, and block-entity-specific access; fail closed for restricted data it cannot verify.
- [ ] **Step 4: Run focused tests and the full test suite; verify no truncated accepted capture.**
- [ ] **Step 5: Commit Task 2 files only.**

### Task 3: Server-owned two-corner selection

**Files:** Create `BlueprintCaptureSelection.java`, `BlueprintCaptureCornerPayload.java`, `BlueprintCaptureFeedbackPayload.java`; modify `BlueprintServerHandlers.java`, `BlueprintModule.java`, `EmptyBlueprintItem.java`, `BlueprintTransitionService.java`; test `BlueprintCaptureSelectionTest.java` and `BlueprintTransitionServiceTest.java`.

**Interfaces:** `BlueprintCaptureSelection.click(Context context, BlockPos clicked)` returns `Result(CaptureSelectionState state, BlockPos firstCorner, BlockPos secondCorner)`. `Context` contains player UUID, dimension, selected slot, exact server `ItemStack` reference, and game tick. `FIRST_CORNER` and `COMPLETE` are the result states; the server handler calls `BlueprintCapture.capture(...)` on `COMPLETE`. The corner payload contains only `BlockPos`; feedback contains state, optional first corner, and failure reason.

- [ ] **Step 1: Write failing selection and transition tests.**

```java
BlueprintCaptureSelection.Context context = new BlueprintCaptureSelection.Context(
        UUID.randomUUID(), "minecraft:overworld", 0, new ItemStack(BlueprintItems.EMPTY_BLUEPRINT), 100L);
assertEquals(CaptureSelectionState.FIRST_CORNER, selection.click(context, first).state());
BlueprintCaptureSelection.Result secondResult = selection.click(context, second);
assertEquals(CaptureSelectionState.COMPLETE, secondResult.state());
assertEquals(second, secondResult.secondCorner());
```

Test same-block double click, item replacement in the same slot, timeout, death, and dimension change. Handler/transition tests assert a rejected capture leaves the item Empty and the old client-authored save-design route is absent.

- [ ] **Step 2: Run focused selection/transition tests and confirm missing behavior.**
- [ ] **Step 3: Register minimal corner/cancel/feedback payloads.** On a corner request, independently raycast from the server player at interaction reach and reject a coordinate that is not the server's targeted block. Read only the server world; on success call `BlueprintTransitionService.saveDesign` with the server-produced snapshot and write the held item once. Clear selection on completion, rejection, timeout, invalid item, death, disconnect, or dimension change. Remove the arbitrary save-design payload handler/registration.
- [ ] **Step 4: Run focused and full tests; inspect rejection messages and item state.**
- [ ] **Step 5: Commit Task 3 files only.**

### Task 4: Client selection UI and removal of design mode

**Files:** Modify `BlueprintClientController.java`, `TheEconomistClient.java`, `BlueprintHudOverlay.java`, `BlueprintSessionModel.java`, `BlueprintSessionMode.java`; create `BlueprintCaptureInput.java`; remove obsolete `BlueprintSoulCamera.java`, mouse mixin/config registration, draft-only input hooks, and design-only tests/files after accounting for dirty edits; test `BlueprintSessionModelTest.java` and `BlueprintCaptureInputTest.java`.

**Interfaces:** `BlueprintCaptureInput.consumeBlockUse(BlueprintState state, InteractionHand hand)` returns true only for an Empty Blueprint in the main hand; the callback sends `BlueprintCaptureCornerPayload` and consumes vanilla use. Feedback updates a client-only selection outline. Designed Blueprint still enters existing placement mode. `BlueprintClientController.cancel()` clears selection and sends cancel intent when connected.

- [ ] **Step 1: Write failing client-session tests.**

```java
BlueprintSessionModel model = BlueprintSessionModel.selecting();
model.firstCorner(new BlockPos(3, 70, 4));
assertEquals(new BlockPos(3, 70, 4), model.firstCorner());
assertTrue(model.cancel());
assertNull(model.firstCorner());
```

Test feedback rejections clear the outline; item loss, Escape, death, dimension change, and disconnect cancel. Preserve placement rotation and pending-request tests.

```java
assertTrue(BlueprintCaptureInput.consumeBlockUse(BlueprintState.EMPTY, InteractionHand.MAIN_HAND));
assertFalse(BlueprintCaptureInput.consumeBlockUse(BlueprintState.EMPTY, InteractionHand.OFF_HAND));
assertFalse(BlueprintCaptureInput.consumeBlockUse(BlueprintState.DESIGNED, InteractionHand.MAIN_HAND));
```

- [ ] **Step 2: Run focused session tests and confirm the selection state is absent.**
- [ ] **Step 3: Replace design callbacks with capture callbacks.** Intercept right-click on a real block while Empty Blueprint is held and send corner intent without opening chest/container UI. Keep placement callback and planned-preview map. Render first corner/selection outline and HUD hint. Remove camera/input replacement and design-only code/config without erasing unrelated user edits in `BlueprintClientKeys.java` or the planned-preview functionality.
- [ ] **Step 4: Run the full test suite and `.\gradlew.bat build`; launch `.\gradlew.bat runClient` and verify chest-corner interception in a local world.**
- [ ] **Step 5: Commit only reviewed Task 4 changes.**

### Task 5: Placement compatibility, docs, and end-to-end verification

**Files:** Modify `BlueprintDesign.java` and `README.md`; extend `BlueprintTransitionServiceTest.java` and `BlueprintStackDataTest.java`; remove dead save-design payload/codec and old design-mode test files only once references are gone.

**Interfaces:** A captured `BlueprintDesign` is consumed unchanged by placement, preview, fingerprint, and Planned item storage. Legacy `BlueprintDesign` objects still work without block-entity payloads.

- [ ] **Step 1: Write a failing rotation-preservation test and placement regression test.**

```java
BlueprintTransitionResult planned = BlueprintTransitionService.confirmPlacement(
        new BlueprintStackData(BlueprintState.DESIGNED, chestDesign, null),
        placement, "minecraft:overworld", pos -> false,
        BlueprintDesignFingerprint.of(chestDesign));
assertTrue(planned.accepted());
assertEquals(chestDesign, planned.data().design());
assertEquals(chestDesign.blocks().get(0).blockEntityData(),
        chestDesign.rotated(1).blocks().get(0).blockEntityData());
```

The rotation assertion must fail until `BlueprintDesign.rotated` carries block-entity data into the rotated block. Also verify an older item round-trips and a changed chest payload is rejected by the expected fingerprint.

- [ ] **Step 2: Run the focused placement tests and verify the rotation assertion fails for lost payload data.**
- [ ] **Step 3: Make the minimal placement/preview changes needed; update `README.md` to document the two clicks, all non-air block/entity data, selection cancellation, size/access rejections, and the fact that Planned does not materialize contents. Remove dead design-mode documentation and code.**
- [ ] **Step 4: Run `./gradlew.bat build`, `git diff --check`, and a manual client test of Empty -> Designed -> Planned. Report any untested multiplayer or protection-mod behavior explicitly.**
- [ ] **Step 5: Commit only Task 5 files, then perform a final diff review against the spec and working-tree baseline.**
