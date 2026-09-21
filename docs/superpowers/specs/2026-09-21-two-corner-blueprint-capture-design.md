# Two-Corner Blueprint Capture Design

## Goal and scope

Replace the detached-camera design mode with capture of an existing world region. An Empty Blueprint selects two block corners by right-clicking; the server captures the non-air blocks in the inclusive cuboid and turns the item into a Designed Blueprint. Designed-to-Planned placement, rotation, and the existing ghost preview remain. Capturing or planning never places blocks, creates items, removes blocks, or moves the player.

"Everything" within a captured block means its exact block state and available block-entity data, including inventory item stacks and components, sign text, and other persistent data. Air and entities are outside the capture. A blueprint records desired content; it is not a source of free materials. Any future construction feature must separately enforce material and permission rules before applying block-entity data.

## Interaction and state flow

An Empty Blueprint in the main hand intercepts right-clicks on blocks before ordinary block interaction (including opening a chest). The first click records the first corner on the server. The client shows a selection marker and a hint to choose the opposite corner. The second click records the second corner and requests capture. A same-block second click is a valid one-block selection. The selected region is inclusive and independent of click order.

The selection belongs to the player, dimension, and exact held blueprint/slot. It expires after a short inactivity period and is cleared on Escape, item change or loss, death, disconnect, or dimension change. A rejected second click reports a reason and leaves the blueprint Empty; selection is cleared so a retry starts from the first corner. No flight proxy, design draft, design-mode block placement, or design-mode attack interception remains.

The client may draw a lightweight outline while selecting, but the outline is visual only. The server is the sole source of captured structure data. The client sends corner intent, never a proposed block palette or block-entity payload. The server checks the clicked blocks against its own interaction/raycast context so client-supplied coordinates cannot extend reach.

## Capture rules and authority

Before reading a region, the server verifies the exact Empty Blueprint is still held, both corners are in the same current dimension, both clicks are within normal interaction reach, the cuboid is within world bounds and loaded chunks, and the volume and maximum axis lengths are within configured limits. It checks interaction/claim permissions throughout the region and checks access to container or restricted block-entity data; inaccessible data rejects the capture rather than silently producing a partial design. No chunk is loaded solely for capture. External protection integrations may be required where a claim mod does not expose its access decision through the standard checks.

The server snapshots block states and block-entity data atomically on its game thread. If any block cannot be encoded or the region exceeds block count, per-entry data, or total serialized-size limits, capture fails without mutating the item. Bounds are checked before allocation and during encoding. A successful snapshot is normalized to the cuboid's minimum corner and written to the held item as Designed in one transition. Existing validation that rejects chests, redstone, command blocks, spawners, and similar block types is removed for **server-captured** designs; unknown or malformed data remains invalid.

Block-entity persistence retains type and functional contents but does not retain absolute world coordinates as active coordinates. Stored data is relative to its block, with positional fields rebased when a future authorized builder applies it. Captured inventory stacks include item counts and components. Restricted data is never sent to other clients as part of the selection process; once on the blueprint item, it follows Minecraft's ordinary item-data visibility rules.

## Data model and compatibility

Extend each saved block with an optional, bounded block-entity payload and version the item-data representation. Legacy Designed and Planned blueprints with block ID and properties only continue to decode with absent block-entity data. The capture operation is server-side, so the save-design payload and codec carrying an arbitrary client-authored design are removed from normal gameplay. Corner-selection messages contain coordinates and selection feedback only.

Existing placement rotation and mirroring continue to transform block positions; saved block states and attached block-entity payloads remain associated with their owning blocks. This change does not add state-orientation or block-entity rotation rules for future construction. The design fingerprint includes canonical block-entity data so a changed chest inventory or sign text invalidates a stale placement preview. Preview rendering still uses block models/states; it does not render chest contents or full sign text. Planned blueprints retain captured data without materializing it.

The existing 64-block maximum axis length and 32,768-block maximum count remain upper bounds. Introduce a capture-volume bound and explicit byte caps for individual block-entity payloads and the whole item, with conservative values set during implementation to remain below Minecraft packet/item-component limits. Oversized selections are rejected with a clear message, never truncated.

## Failure handling

- Clicking air does not select a corner; the player is prompted to click a block.
- A changed, non-Empty, or missing blueprint cannot complete an old selection.
- Unloaded, out-of-bounds, protected, inaccessible, oversized, or unserializable regions are rejected without changing the world or item.
- A region containing only air is rejected as an empty design.
- Selection cleanup is idempotent and does not affect ordinary inventory, movement, or camera state.
- Placement retains its existing server-side checks for occupied and protected destinations and its existing Designed-to-Planned transition.

## Verification

Test-first coverage includes click-order normalization; one-block and empty captures; state and block-entity round-trips (including a chest with item components and sign text); size, access, world-bound, and loaded-chunk rejection; legacy item decoding; fingerprint changes when block-entity data changes; and unchanged placement behavior. The full Gradle build must pass. Manual single-player and multiplayer checks cover both corners, chest clicks not opening the container during selection, cancellation, item switching, capture visibility, and preserved Planned preview behavior.
