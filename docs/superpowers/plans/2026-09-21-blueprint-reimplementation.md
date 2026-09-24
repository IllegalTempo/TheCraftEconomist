# Blueprint Reimplementation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the interactive blueprint workflow so a vulnerable stationary body controls a private flying design viewpoint, fake blocks render without world mutation, and only the server commits Designed and Planned item states.

**Architecture:** Keep draft editing, transitions, transforms, and validation in focused testable domain types. A client session controller composes a detached camera/input adapter, virtual targeting, shared ghost renderer, key/HUD controls, and bounded C2S intents; server handlers apply atomic state transitions to the held blueprint.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, JUnit Jupiter 5.13.4, Gradle/Loom 1.17.

**Spec:** `docs/superpowers/specs/2026-09-21-blueprint-reimplementation-design.md`

## Global Constraints

- `README.md` is the behavioral source of truth for the interactive blueprint workflow.
- The real server-owned player remains stationary, visible, and vulnerable throughout design mode.
- Design and placement previews never call world block mutation APIs.
- Design-mode soul movement has no 32-block radius cancellation.
- Existing Designed and Planned item serialization remains readable.
- Blueprint dimensions and block counts remain bounded by `BlueprintLimits`.
- State transitions are only `EMPTY -> DESIGNED -> PLANNED` and are atomic and server-authoritative.
- Construction contracts, builder AI, bills of materials, JSON built-ins, mirroring controls, and construction execution remain out of scope.

## Review Focus

- Drafts spanning negative world coordinates must normalize without losing relative geometry; Task 1 pins this with `normalizes_negative_world_coordinates`.
- Malformed block-state properties must be rejected before persistence; Task 2 pins this with `rejects_unknown_block_state_property`.
- A placement confirmation sent after the held item changed must preserve the new item and reject the stale request; Task 2 pins this with `rejects_stale_placement_transition`.
- Repeated cancellation and cleanup after death or item loss must restore camera/input exactly once; Task 3 pins this with `cleanup_is_idempotent` and `invalid_session_context_requests_cleanup`.
- A destination that becomes occupied after preview began must be rechecked at confirmation; Task 2 pins this with `rechecks_occupied_positions_when_planning`.

---

### Task 1: Draft model and exact block-state snapshots

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintBlockSnapshot.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintDraft.java`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintDraftTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintDesign.java`
- Modify: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintDesignTest.java`

**Interfaces:**
- Consumes: `BlueprintBlock`, `BlueprintDesign`, `BlueprintLimits`, and Minecraft `BlockPos`.
- Produces: `BlueprintBlockSnapshot(String blockId, String stateProperties)` and mutable draft operations `put`, `remove`, `blocks`, `isEmpty`, and `normalize`.

- [ ] **Step 1: Write focused failing draft tests**

```java
class BlueprintDraftTest {
    @Test
    void adds_replaces_and_removes_private_blocks() {
        BlueprintDraft draft = new BlueprintDraft();
        BlockPos position = new BlockPos(4, 70, -2);
        draft.put(position, new BlueprintBlockSnapshot("minecraft:stone", ""));
        draft.put(position, new BlueprintBlockSnapshot("minecraft:oak_stairs", "facing=east,half=bottom"));

        assertEquals("minecraft:oak_stairs", draft.blocks().get(position).blockId());
        assertEquals(new BlueprintBlockSnapshot("minecraft:oak_stairs", "facing=east,half=bottom"), draft.remove(position));
        assertTrue(draft.isEmpty());
    }

    @Test
    void normalizes_negative_world_coordinates() {
        BlueprintDraft draft = new BlueprintDraft();
        draft.put(new BlockPos(-5, 63, -8), new BlueprintBlockSnapshot("minecraft:stone", ""));
        draft.put(new BlockPos(-3, 65, -7), new BlueprintBlockSnapshot("minecraft:oak_slab", "type=top"));

        BlueprintDesign design = draft.normalize();

        assertEquals(3, design.width());
        assertEquals(3, design.height());
        assertEquals(2, design.depth());
        assertTrue(design.blocks().contains(new BlueprintBlock(0, 0, 0, "minecraft:stone", "")));
        assertTrue(design.blocks().contains(new BlueprintBlock(2, 2, 1, "minecraft:oak_slab", "type=top")));
    }

    @Test
    void refuses_to_normalize_an_empty_draft() {
        assertThrows(IllegalStateException.class, () -> new BlueprintDraft().normalize());
    }
}
```

- [ ] **Step 2: Run the draft tests and verify RED**

Run: `.\gradlew.bat test --tests '*BlueprintDraftTest'`

Expected: compilation fails because `BlueprintDraft` and `BlueprintBlockSnapshot` do not exist.

- [ ] **Step 3: Implement the minimal snapshot and draft model**

```java
public record BlueprintBlockSnapshot(String blockId, String stateProperties) {
    public BlueprintBlockSnapshot {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(stateProperties, "stateProperties");
        if (blockId.isBlank()) throw new IllegalArgumentException("block id must not be blank");
    }
}

public final class BlueprintDraft {
    private final Map<BlockPos, BlueprintBlockSnapshot> blocks = new LinkedHashMap<>();

    public BlueprintBlockSnapshot put(BlockPos position, BlueprintBlockSnapshot block) {
        return blocks.put(Objects.requireNonNull(position), Objects.requireNonNull(block));
    }

    public BlueprintBlockSnapshot remove(BlockPos position) { return blocks.remove(position); }
    public boolean isEmpty() { return blocks.isEmpty(); }
    public Map<BlockPos, BlueprintBlockSnapshot> blocks() { return Map.copyOf(blocks); }
    public void clear() { blocks.clear(); }

    public BlueprintDesign normalize() {
        if (blocks.isEmpty()) throw new IllegalStateException("cannot normalize an empty blueprint draft");
        int minX = blocks.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = blocks.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxY = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int maxZ = blocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        List<BlueprintBlock> normalized = blocks.entrySet().stream()
                .map(entry -> new BlueprintBlock(entry.getKey().getX() - minX,
                        entry.getKey().getY() - minY,
                        entry.getKey().getZ() - minZ,
                        entry.getValue().blockId(), entry.getValue().stateProperties()))
                .toList();
        return new BlueprintDesign(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, normalized);
    }
}
```

- [ ] **Step 4: Add a rotation test that preserves block-state text**

```java
@Test
void rotation_preserves_serialized_block_state() {
    BlueprintDesign design = new BlueprintDesign(1, 1, 1,
            List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_stairs", "facing=east,half=bottom")));

    assertEquals("facing=east,half=bottom", design.rotated(1).blocks().getFirst().stateProperties());
}
```

- [ ] **Step 5: Run the focused tests and the full blueprint unit suite**

Run: `.\gradlew.bat test --tests '*BlueprintDraftTest' --tests '*BlueprintDesignTest'`

Expected: PASS with no warnings.

- [ ] **Step 6: Commit the draft model**

```powershell
git add src/main/java/com/jedts/theeconomist/blueprint/BlueprintBlockSnapshot.java src/main/java/com/jedts/theeconomist/blueprint/BlueprintDraft.java src/main/java/com/jedts/theeconomist/blueprint/BlueprintDesign.java src/test/java/com/jedts/theeconomist/blueprint/BlueprintDraftTest.java src/test/java/com/jedts/theeconomist/blueprint/BlueprintDesignTest.java
git commit -m "feat: add blueprint draft model"
```

### Task 2: Atomic server-authoritative lifecycle transitions

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintTransitionResult.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintTransitionService.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/SaveBlueprintDesignPayload.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/ConfirmBlueprintPlacementPayload.java`
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintPayloadCodec.java`
- Create: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintTransitionServiceTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintValidator.java`
- Modify: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintValidatorTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintServerHandlers.java`
- Modify: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintModule.java`
- Delete: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintUpdatePayload.java`

**Interfaces:**
- Consumes: `BlueprintStackData`, `BlueprintValidator`, `Predicate<BlockPos>`, and the server's current dimension.
- Produces: `BlueprintTransitionService.saveDesign(...)`, `BlueprintTransitionService.confirmPlacement(...)`, and separate bounded payloads whose placement payload contains no client-supplied design.

- [ ] **Step 1: Write failing transition tests**

```java
class BlueprintTransitionServiceTest {
    private static final BlueprintDesign DESIGN = new BlueprintDesign(1, 1, 1,
            List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));

    @Test
    void saves_only_valid_designs_on_empty_blueprints() {
        BlueprintStackData empty = new BlueprintStackData(BlueprintState.EMPTY, null, null);
        BlueprintTransitionResult result = BlueprintTransitionService.saveDesign(empty, DESIGN);

        assertTrue(result.accepted());
        assertEquals(BlueprintState.DESIGNED, result.data().state());
        assertEquals(DESIGN, result.data().design());
    }

    @Test
    void rejects_stale_placement_transition() {
        BlueprintStackData changed = new BlueprintStackData(BlueprintState.PLANNED, DESIGN,
                new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 0, false, false));
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(changed,
                new BlueprintPlacement("minecraft:overworld", new BlockPos(5, 70, 5), 0, false, false),
                "minecraft:overworld", position -> false);

        assertFalse(result.accepted());
        assertSame(changed, result.data());
    }

    @Test
    void rechecks_occupied_positions_when_planning() {
        BlueprintStackData designed = new BlueprintStackData(BlueprintState.DESIGNED, DESIGN, null);
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(designed,
                new BlueprintPlacement("minecraft:overworld", new BlockPos(5, 70, 5), 0, false, false),
                "minecraft:overworld", position -> position.equals(new BlockPos(5, 70, 5)));

        assertFalse(result.accepted());
        assertEquals(BlueprintState.DESIGNED, result.data().state());
    }
}
```

- [ ] **Step 2: Run the transition tests and verify RED**

Run: `.\gradlew.bat test --tests '*BlueprintTransitionServiceTest'`

Expected: compilation fails because the transition service and result do not exist.

- [ ] **Step 3: Implement atomic transition results and service**

```java
public record BlueprintTransitionResult(boolean accepted, BlueprintStackData data, String reason) {
    public static BlueprintTransitionResult accepted(BlueprintStackData data) {
        return new BlueprintTransitionResult(true, data, "");
    }

    public static BlueprintTransitionResult rejected(BlueprintStackData current, String reason) {
        return new BlueprintTransitionResult(false, current, reason);
    }
}

public final class BlueprintTransitionService {
    public static BlueprintTransitionResult saveDesign(BlueprintStackData current, BlueprintDesign proposed) {
        if (current.state() != BlueprintState.EMPTY) {
            return BlueprintTransitionResult.rejected(current, "blueprint is not empty");
        }
        BlueprintValidationResult validation = BlueprintValidator.validateDesign(proposed);
        if (!validation.valid()) return BlueprintTransitionResult.rejected(current, validation.reason());
        return BlueprintTransitionResult.accepted(new BlueprintStackData(BlueprintState.DESIGNED, proposed, null));
    }

    public static BlueprintTransitionResult confirmPlacement(BlueprintStackData current, BlueprintPlacement proposed,
                                                               String currentDimension, Predicate<BlockPos> occupied) {
        if (current.state() != BlueprintState.DESIGNED || current.design() == null) {
            return BlueprintTransitionResult.rejected(current, "blueprint is not designed");
        }
        if (proposed == null || !proposed.dimension().equals(currentDimension)) {
            return BlueprintTransitionResult.rejected(current, "placement must be in the current dimension");
        }
        BlueprintValidationResult validation = BlueprintValidator.validatePlacement(current.design(), proposed, occupied);
        if (!validation.valid()) return BlueprintTransitionResult.rejected(current, validation.reason());
        return BlueprintTransitionResult.accepted(new BlueprintStackData(BlueprintState.PLANNED, current.design(), proposed));
    }
}
```

- [ ] **Step 4: Write validator tests for malformed block-state properties**

```java
@Test
void rejects_unknown_block_state_property() {
    BlueprintDesign design = new BlueprintDesign(1, 1, 1,
            List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_stairs", "not_a_property=east")));

    BlueprintValidationResult result = BlueprintValidator.validateDesign(design);

    assertFalse(result.valid());
    assertTrue(result.reason().contains("block state"));
}
```

- [ ] **Step 5: Validate identifiers and every serialized state property against the block registry**

```java
private static BlueprintValidationResult validateBlock(BlueprintBlock block) {
    Identifier id = Identifier.tryParse(block.blockId());
    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
        return BlueprintValidationResult.rejected("unknown block id: " + block.blockId());
    }
    BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    for (String encoded : block.stateProperties().split(",")) {
        if (encoded.isBlank()) continue;
        String[] pair = encoded.split("=", 2);
        Property<?> property = pair.length == 2 ? state.getBlock().getStateDefinition().getProperty(pair[0]) : null;
        if (property == null || property.getValue(pair[1]).isEmpty()) {
            return BlueprintValidationResult.rejected("invalid block state property: " + encoded);
        }
    }
    return BlueprintValidationResult.accepted();
}
```

- [ ] **Step 6: Replace the ambiguous update payload with two intent payloads**

```java
public final class BlueprintPayloadCodec {
    public static void writeDesign(RegistryFriendlyByteBuf buf, BlueprintDesign design) {
        buf.writeVarInt(design.width());
        buf.writeVarInt(design.height());
        buf.writeVarInt(design.depth());
        buf.writeVarInt(design.blocks().size());
        for (BlueprintBlock block : design.blocks()) {
            buf.writeVarInt(block.x());
            buf.writeVarInt(block.y());
            buf.writeVarInt(block.z());
            buf.writeUtf(block.blockId(), 256);
            buf.writeUtf(block.stateProperties(), 256);
        }
    }

    public static BlueprintDesign readDesign(RegistryFriendlyByteBuf buf) {
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int depth = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > BlueprintLimits.MAX_BLOCKS) {
            throw new IllegalArgumentException("invalid blueprint block count");
        }
        List<BlueprintBlock> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            blocks.add(new BlueprintBlock(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(256), buf.readUtf(256)));
        }
        return new BlueprintDesign(width, height, depth, blocks);
    }

    public static void writePlacement(RegistryFriendlyByteBuf buf, BlueprintPlacement placement) {
        buf.writeUtf(placement.dimension(), 128);
        buf.writeBlockPos(placement.origin());
        buf.writeVarInt(placement.rotation());
        buf.writeBoolean(placement.mirrorX());
        buf.writeBoolean(placement.mirrorZ());
    }

    public static BlueprintPlacement readPlacement(RegistryFriendlyByteBuf buf) {
        return new BlueprintPlacement(buf.readUtf(128), buf.readBlockPos(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean());
    }
}

public record SaveBlueprintDesignPayload(BlueprintDesign design) implements CustomPacketPayload {
    public static final Type<SaveBlueprintDesignPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/save_blueprint_design");
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveBlueprintDesignPayload> CODEC =
            StreamCodec.of((buf, payload) -> BlueprintPayloadCodec.writeDesign(buf, payload.design()),
                    buf -> new SaveBlueprintDesignPayload(BlueprintPayloadCodec.readDesign(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

public record ConfirmBlueprintPlacementPayload(BlueprintPlacement placement) implements CustomPacketPayload {
    public static final Type<ConfirmBlueprintPlacementPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/confirm_blueprint_placement");
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmBlueprintPlacementPayload> CODEC =
            StreamCodec.of((buf, payload) -> BlueprintPayloadCodec.writePlacement(buf, payload.placement()),
                    buf -> new ConfirmBlueprintPlacementPayload(BlueprintPayloadCodec.readPlacement(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

The placement codec writes only dimension, origin, rotation, and mirror flags; the server obtains the design from its held item.

- [ ] **Step 7: Make server handlers write only accepted transition data**

```java
private static void saveDesign(ServerPlayer player, BlueprintDesign proposed) {
    ItemStack stack = heldBlueprint(player);
    if (stack == null) return;
    BlueprintTransitionResult result = BlueprintTransitionService.saveDesign(BlueprintStackData.read(stack), proposed);
    if (!result.accepted()) {
        player.sendSystemMessage(Component.literal("Design rejected: " + result.reason()));
        return;
    }
    BlueprintStackData.write(stack, result.data());
    player.sendSystemMessage(Component.literal("Blueprint design saved."));
}

private static void confirmPlacement(ServerPlayer player, BlueprintPlacement proposed) {
    ItemStack stack = heldBlueprint(player);
    if (stack == null) return;
    BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(BlueprintStackData.read(stack), proposed,
            player.level().dimension().identifier().toString(), position -> !player.level().getBlockState(position).isAir());
    if (!result.accepted()) {
        player.sendSystemMessage(Component.literal("Placement rejected: " + result.reason()));
        return;
    }
    BlueprintStackData.write(stack, result.data());
    player.sendSystemMessage(Component.literal("Blueprint planned at " + proposed.origin().toShortString() + "."));
}

private static ItemStack heldBlueprint(ServerPlayer player) {
    ItemStack stack = player.getMainHandItem();
    if (stack.getItem() instanceof EmptyBlueprintItem) return stack;
    player.sendSystemMessage(Component.literal("Hold a Blueprint in your main hand."));
    return null;
}
```

Expose one public writer that updates both the lifecycle display name and custom data atomically, and keep `setDesigned`/`setPlanned` delegating to it for compatibility:

```java
public static void write(ItemStack stack, BlueprintStackData data) {
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(BlueprintDisplayName.forState(data.state())));
    CustomData.set(DataComponents.CUSTOM_DATA, stack, writeTag(data));
}
```

Register both payloads and their handlers in `BlueprintModule`:

```java
PayloadTypeRegistry.serverboundPlay().register(SaveBlueprintDesignPayload.TYPE, SaveBlueprintDesignPayload.CODEC);
PayloadTypeRegistry.serverboundPlay().register(ConfirmBlueprintPlacementPayload.TYPE,
        ConfirmBlueprintPlacementPayload.CODEC);
BlueprintServerHandlers.register();
```

- [ ] **Step 8: Run transition, validator, serialization, and full tests**

Run: `.\gradlew.bat test --tests '*BlueprintTransitionServiceTest' --tests '*BlueprintValidatorTest' --tests '*BlueprintStackDataTest'`

Expected: focused tests PASS.

Run: `.\gradlew.bat test`

Expected: entire suite PASS.

- [ ] **Step 9: Commit server-authoritative transitions**

```powershell
git add src/main/java/com/jedts/theeconomist/blueprint src/test/java/com/jedts/theeconomist/blueprint
git commit -m "fix: make blueprint transitions server authoritative"
```

### Task 3: Detached soul camera and session lifecycle

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/blueprint/BlueprintSessionMode.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintSessionModel.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintSoulCamera.java`
- Create: `src/test/java/com/jedts/theeconomist/client/BlueprintSessionModelTest.java`
- Rewrite: `src/main/java/com/jedts/theeconomist/client/BlueprintClientController.java`

**Interfaces:**
- Consumes: `BlueprintDraft`, `BlueprintDesign`, `BlueprintPlacement`, `Minecraft`, and the two C2S intent payloads from Task 2.
- Produces: `BlueprintClientController.handleBlueprintUse(ItemStack)`, `useBlock(BlockItem)`, `attack()`, `tick(Minecraft)`, `render(LevelRenderContext)`, `rotatePlacement()`, `cancel()`, `designing()`, and `placing()`.

- [ ] **Step 1: Write failing pure lifecycle tests**

```java
class BlueprintSessionModelTest {
    @Test
    void rotates_placement_in_quarter_turns() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintSessionModel session = BlueprintSessionModel.placing(design);

        session.rotate();
        session.rotate();
        session.rotate();
        session.rotate();

        assertEquals(0, session.rotation());
    }

    @Test
    void cleanup_is_idempotent() {
        BlueprintSessionModel session = BlueprintSessionModel.designing();

        assertTrue(session.cancel());
        assertFalse(session.cancel());
        assertEquals(BlueprintSessionMode.NONE, session.mode());
    }

    @Test
    void invalid_session_context_requests_cleanup() {
        BlueprintSessionModel session = BlueprintSessionModel.designing();

        assertTrue(session.shouldCancel(false, true, true, false));
        assertTrue(session.shouldCancel(true, false, true, false));
        assertTrue(session.shouldCancel(true, true, false, false));
        assertTrue(session.shouldCancel(true, true, true, true));
    }
}
```

The four context arguments are `playerAlive`, `sameDimension`, `hasRelevantBlueprint`, and `disconnected`.

- [ ] **Step 2: Run the lifecycle tests and verify RED**

Run: `.\gradlew.bat test --tests '*BlueprintSessionModelTest'`

Expected: compilation fails because the session model does not exist.

- [ ] **Step 3: Implement the minimal lifecycle model**

```java
public enum BlueprintSessionMode { NONE, DESIGN, PLACEMENT }

public final class BlueprintSessionModel {
    private BlueprintSessionMode mode;
    private final BlueprintDraft draft;
    private final BlueprintDesign design;
    private int rotation;

    private BlueprintSessionModel(BlueprintSessionMode mode, BlueprintDraft draft, BlueprintDesign design) {
        this.mode = mode;
        this.draft = draft;
        this.design = design;
    }

    public static BlueprintSessionModel idle() {
        return new BlueprintSessionModel(BlueprintSessionMode.NONE, null, null);
    }

    public static BlueprintSessionModel designing() {
        return new BlueprintSessionModel(BlueprintSessionMode.DESIGN, new BlueprintDraft(), null);
    }

    public static BlueprintSessionModel placing(BlueprintDesign design) {
        return new BlueprintSessionModel(BlueprintSessionMode.PLACEMENT, null, Objects.requireNonNull(design));
    }

    public void rotate() {
        if (mode == BlueprintSessionMode.PLACEMENT) rotation = (rotation + 1) % 4;
    }

    public BlueprintSessionMode mode() { return mode; }
    public BlueprintDraft draft() { return draft; }
    public BlueprintDesign design() { return design; }
    public int rotation() { return rotation; }

    public boolean cancel() {
        if (mode == BlueprintSessionMode.NONE) return false;
        mode = BlueprintSessionMode.NONE;
        if (draft != null) draft.clear();
        return true;
    }

    public boolean shouldCancel(boolean playerAlive, boolean sameDimension,
                                boolean hasRelevantBlueprint, boolean disconnected) {
        return mode != BlueprintSessionMode.NONE
                && (!playerAlive || !sameDimension || !hasRelevantBlueprint || disconnected);
    }
}
```

- [ ] **Step 4: Implement the detached client-only camera/input adapter**

```java
public final class BlueprintSoulCamera {
    private Entity previousCamera;
    private ClientInput previousInput;
    private ArmorStand proxy;

    public void attach(Minecraft minecraft) {
        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        previousCamera = minecraft.getCameraEntity();
        previousInput = player.input;
        proxy = new ArmorStand(minecraft.level, player.getX(), player.getY(), player.getZ());
        proxy.setInvisible(true);
        proxy.setNoGravity(true);
        proxy.setYRot(player.getYRot());
        proxy.setXRot(player.getXRot());
        player.input = new ClientInput();
        minecraft.setCameraEntity(proxy);
    }

    public void tick(Minecraft minecraft) {
        if (proxy == null || minecraft.player == null) return;
        proxy.setYRot(minecraft.player.getYRot());
        proxy.setXRot(minecraft.player.getXRot());
        Vec3 forward = Vec3.directionFromRotation(0.0F, proxy.getYRot());
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 motion = Vec3.ZERO;
        if (minecraft.options.keyUp.isDown()) motion = motion.add(forward);
        if (minecraft.options.keyDown.isDown()) motion = motion.subtract(forward);
        if (minecraft.options.keyRight.isDown()) motion = motion.add(right);
        if (minecraft.options.keyLeft.isDown()) motion = motion.subtract(right);
        if (minecraft.options.keyJump.isDown()) motion = motion.add(0.0, 1.0, 0.0);
        if (minecraft.options.keyShift.isDown()) motion = motion.add(0.0, -1.0, 0.0);
        if (motion.lengthSqr() > 0.0) proxy.setPos(proxy.position().add(motion.normalize().scale(0.35)));
    }

    public void detach(Minecraft minecraft) {
        if (minecraft.player != null && previousInput != null) minecraft.player.input = previousInput;
        if (previousCamera != null) minecraft.setCameraEntity(previousCamera);
        previousCamera = null;
        previousInput = null;
        proxy = null;
    }

    public Vec3 eyePosition() { return proxy.getEyePosition(); }
    public Vec3 lookDirection() { return proxy.getLookAngle(); }
}
```

The cached Minecraft 26.3 signatures confirm the `ArmorStand(Level,double,double,double)` constructor, public `LocalPlayer.input`, `Minecraft.setCameraEntity(Entity)`, and `Options.keyUp`, `keyDown`, `keyLeft`, `keyRight`, `keyJump`, and `keyShift` fields used above.

- [ ] **Step 5: Rewrite the controller around the model and camera adapter**

```java
public static void handleBlueprintUse(ItemStack stack) {
    BlueprintStackData data = BlueprintStackData.read(stack);
    switch (BlueprintItemBehavior.action(data.state(), session.mode())) {
        case SAVE_DESIGN -> {
            if (session.draft().isEmpty()) {
                minecraft().player.sendOverlayMessage(Component.literal("Build at least one fake block first."));
                return;
            }
            ClientPlayNetworking.send(new SaveBlueprintDesignPayload(session.draft().normalize()));
            cancel();
        }
        case CONFIRM_PLACEMENT -> {
            ClientPlayNetworking.send(new ConfirmBlueprintPlacementPayload(currentPlacement()));
            cancel();
        }
        case DESIGN -> startDesign();
        case PLACE -> startPlacement(data.design());
        case NONE -> minecraft().player.sendOverlayMessage(Component.literal("This blueprint is already planned."));
    }
}
```

The controller owns these explicit fields:

```java
private static BlueprintSessionModel session = BlueprintSessionModel.idle();
private static final BlueprintSoulCamera soulCamera = new BlueprintSoulCamera();
private static String startingDimension;
private static int blueprintSlot = -1;

private static void startDesign() {
    Minecraft minecraft = minecraft();
    session = BlueprintSessionModel.designing();
    startingDimension = minecraft.level.dimension().identifier().toString();
    blueprintSlot = minecraft.player.getInventory().getSelectedSlot();
    soulCamera.attach(minecraft);
}

public static void cancel() {
    if (!session.cancel()) return;
    soulCamera.detach(minecraft());
    designRenderBlocks.clear();
    placementRenderBlocks.clear();
    startingDimension = null;
    blueprintSlot = -1;
}
```

`startPlacement(BlueprintDesign)` assigns `BlueprintSessionModel.placing(design)` and records the selected blueprint slot and current dimension without attaching the soul camera. Do not set player abilities, invisibility, invulnerability, or position.

- [ ] **Step 6: Run lifecycle and full unit tests**

Run: `.\gradlew.bat test --tests '*BlueprintSessionModelTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: entire suite PASS.

- [ ] **Step 7: Commit detached camera lifecycle**

```powershell
git add src/main/java/com/jedts/theeconomist/blueprint/BlueprintSessionMode.java src/main/java/com/jedts/theeconomist/client/BlueprintSessionModel.java src/main/java/com/jedts/theeconomist/client/BlueprintSoulCamera.java src/main/java/com/jedts/theeconomist/client/BlueprintClientController.java src/test/java/com/jedts/theeconomist/client/BlueprintSessionModelTest.java
git commit -m "feat: detach blueprint design camera"
```

### Task 4: Virtual targeting and shared ghost rendering

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintTarget.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintTargeting.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintBlockStateCodec.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintGhostRenderer.java`
- Create: `src/test/java/com/jedts/theeconomist/client/BlueprintTargetingTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/BlueprintClientController.java`

**Interfaces:**
- Consumes: soul or player eye/look vectors, real `BlockHitResult`, draft block positions, `BlueprintBlockSnapshot`, and `BlueprintPlacement`.
- Produces: `BlueprintTarget(addPosition, removePosition)`, state encode/decode helpers, and one renderer for design and placement maps.

- [ ] **Step 1: Write failing target-selection tests**

```java
class BlueprintTargetingTest {
    @Test
    void attaches_to_the_nearest_fake_block_face() {
        Vec3 eye = new Vec3(0.5, 0.5, -2.0);
        Vec3 look = new Vec3(0.0, 0.0, 1.0);

        BlueprintTarget target = BlueprintTargeting.select(eye, look, 6.0,
                Set.of(new BlockPos(0, 0, 0)), Optional.empty());

        assertEquals(new BlockPos(0, 0, -1), target.addPosition());
        assertEquals(new BlockPos(0, 0, 0), target.removePosition());
    }

    @Test
    void places_the_first_block_four_blocks_ahead_when_nothing_is_hit() {
        BlueprintTarget target = BlueprintTargeting.select(new Vec3(0.5, 64.5, 0.5),
                new Vec3(0.0, 0.0, 1.0), 6.0, Set.of(), Optional.empty());

        assertEquals(new BlockPos(0, 64, 4), target.addPosition());
        assertNull(target.removePosition());
    }

    @Test
    void chooses_the_nearer_of_real_and_fake_hits() {
        BlockHitResult real = new BlockHitResult(new Vec3(0.5, 0.5, 3.0), Direction.NORTH,
                new BlockPos(0, 0, 3), false);

        BlueprintTarget target = BlueprintTargeting.select(new Vec3(0.5, 0.5, -2.0),
                new Vec3(0.0, 0.0, 1.0), 8.0, Set.of(new BlockPos(0, 0, 0)), Optional.of(real));

        assertEquals(new BlockPos(0, 0, -1), target.addPosition());
    }
}
```

- [ ] **Step 2: Run targeting tests and verify RED**

Run: `.\gradlew.bat test --tests '*BlueprintTargetingTest'`

Expected: compilation fails because target types do not exist.

- [ ] **Step 3: Implement unit-cube ray targeting**

```java
public record BlueprintTarget(BlockPos addPosition, BlockPos removePosition) { }

public final class BlueprintTargeting {
    public static BlueprintTarget select(Vec3 eye, Vec3 look, double reach, Set<BlockPos> fakeBlocks,
                                         Optional<BlockHitResult> realHit) {
        Vec3 end = eye.add(look.normalize().scale(reach));
        FakeHit fakeHit = fakeBlocks.stream()
                .map(position -> clip(position, eye, end))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(hit -> hit.location().distanceToSqr(eye)))
                .orElse(null);
        double realDistance = realHit.map(hit -> hit.getLocation().distanceToSqr(eye)).orElse(Double.MAX_VALUE);
        if (fakeHit != null && fakeHit.location().distanceToSqr(eye) < realDistance) {
            return new BlueprintTarget(fakeHit.position().relative(fakeHit.face()), fakeHit.position());
        }
        if (realHit.isPresent()) {
            BlockHitResult hit = realHit.get();
            return new BlueprintTarget(hit.getBlockPos().relative(hit.getDirection()), null);
        }
        return new BlueprintTarget(BlockPos.containing(eye.add(look.normalize().scale(4.0))), null);
    }
}
```

```java
private record FakeHit(BlockPos position, Direction face, Vec3 location) { }

private static Optional<FakeHit> clip(BlockPos position, Vec3 eye, Vec3 end) {
    AABB box = new AABB(position);
    Optional<Vec3> clipped = box.clip(eye, end);
    if (clipped.isEmpty()) return Optional.empty();
    Vec3 point = clipped.get();
    double epsilon = 1.0E-6;
    Direction face;
    if (Math.abs(point.x - box.minX) < epsilon) face = Direction.WEST;
    else if (Math.abs(point.x - box.maxX) < epsilon) face = Direction.EAST;
    else if (Math.abs(point.y - box.minY) < epsilon) face = Direction.DOWN;
    else if (Math.abs(point.y - box.maxY) < epsilon) face = Direction.UP;
    else if (Math.abs(point.z - box.minZ) < epsilon) face = Direction.NORTH;
    else face = Direction.SOUTH;
    return Optional.of(new FakeHit(position, face, point));
}
```

- [ ] **Step 4: Extract exact block-state serialization and resolution**

```java
public final class BlueprintBlockStateCodec {
    public static BlueprintBlockSnapshot encode(BlockState state) {
        StringJoiner properties = new StringJoiner(",");
        for (Property<?> property : state.getProperties()) properties.add(encodeProperty(state, property));
        return new BlueprintBlockSnapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties.toString());
    }

    public static Optional<BlockState> decode(BlueprintBlockSnapshot snapshot) {
        Identifier id = Identifier.tryParse(snapshot.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Optional.empty();
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for (String encoded : snapshot.stateProperties().split(",")) {
            if (encoded.isBlank()) continue;
            String[] pair = encoded.split("=", 2);
            state = apply(state, pair[0], pair[1]);
        }
        return Optional.of(state);
    }

    @SuppressWarnings("unchecked")
    private static String encodeProperty(BlockState state, Property<?> property) {
        return encodeTypedProperty(state, (Property<? extends Comparable>) property);
    }

    private static <T extends Comparable<T>> String encodeTypedProperty(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    @SuppressWarnings("unchecked")
    private static BlockState apply(BlockState state, String name, String value) {
        Property<? extends Comparable<?>> raw = state.getBlock().getStateDefinition().getProperty(name);
        if (raw == null) throw new IllegalArgumentException("unknown block state property: " + name);
        return applyTyped(state, (Property) raw, value);
    }

    private static <T extends Comparable<T>> BlockState applyTyped(BlockState state, Property<T> property,
                                                                   String value) {
        T parsed = property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("invalid block state value: " + value));
        return state.setValue(property, parsed);
    }
}
```

- [ ] **Step 5: Extract a renderer with no world writes**

```java
public final class BlueprintGhostRenderer {
    public void render(LevelRenderContext context, Map<BlockPos, BlockState> blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(entry.getValue());
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(position.asLong()), parts);
            context.poseStack().pushPose();
            context.poseStack().translate(position.getX() - camera.x, position.getY() - camera.y,
                    position.getZ() - camera.z);
            context.submitNodeCollector().submitBlockModel(context.poseStack(), RenderTypes.translucentMovingBlock(),
                    parts, BlockModelRenderState.EMPTY_TINTS, 15728880, OverlayTexture.NO_OVERLAY,
                    ARGB.color(128, 40, 130, 255));
            context.poseStack().popPose();
        }
    }
}
```

- [ ] **Step 6: Wire design editing and placement preview to maps only**

```java
public static InteractionResult useBlock(BlockItem blockItem) {
    if (!designing()) return InteractionResult.PASS;
    BlueprintTarget target = currentDesignTarget();
    BlockState state = blockItem.getBlock().defaultBlockState();
    session.draft().put(target.addPosition(), BlueprintBlockStateCodec.encode(state));
    refreshDesignRenderMap();
    return InteractionResult.SUCCESS;
}

public static InteractionResult attack() {
    if (!designing()) return InteractionResult.PASS;
    BlueprintTarget target = currentDesignTarget();
    if (target.removePosition() != null) session.draft().remove(target.removePosition());
    refreshDesignRenderMap();
    return InteractionResult.SUCCESS;
}

private static BlueprintTarget currentDesignTarget() {
    Minecraft minecraft = minecraft();
    Vec3 eye = soulCamera.eyePosition();
    Vec3 look = soulCamera.lookDirection();
    Vec3 end = eye.add(look.normalize().scale(6.0));
    BlockHitResult worldHit = minecraft.level.clip(new ClipContext(eye, end,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
    Optional<BlockHitResult> realHit = worldHit.getType() == HitResult.Type.BLOCK
            ? Optional.of(worldHit) : Optional.empty();
    return BlueprintTargeting.select(eye, look, 6.0, session.draft().blocks().keySet(), realHit);
}

private static void refreshDesignRenderMap() {
    designRenderBlocks.clear();
    for (Map.Entry<BlockPos, BlueprintBlockSnapshot> entry : session.draft().blocks().entrySet()) {
        BlueprintBlockStateCodec.decode(entry.getValue())
                .ifPresent(state -> designRenderBlocks.put(entry.getKey(), state));
    }
}

private static void refreshPlacementRenderMap() {
    placementRenderBlocks.clear();
    BlueprintPlacement placement = currentPlacement();
    for (BlueprintBlock block : session.design().blocks()) {
        BlueprintBlockSnapshot snapshot = new BlueprintBlockSnapshot(block.blockId(), block.stateProperties());
        BlueprintBlockStateCodec.decode(snapshot).ifPresent(state ->
                placementRenderBlocks.put(placement.worldPosition(session.design(), block), state));
    }
}

private static BlueprintPlacement currentPlacement() {
    Minecraft minecraft = minecraft();
    HitResult hit = minecraft.player.pick(32.0, 0.0F, false);
    BlockPos origin = hit instanceof BlockHitResult blockHit
            ? blockHit.getBlockPos().relative(blockHit.getDirection())
            : minecraft.player.blockPosition();
    return new BlueprintPlacement(minecraft.level.dimension().identifier().toString(), origin,
            session.rotation(), false, false);
}
```

Declare `designRenderBlocks` and `placementRenderBlocks` as private `Map<BlockPos, BlockState>` instances owned by the controller. Neither helper reads or writes replacement blocks in the client level.

- [ ] **Step 7: Prove production code no longer mutates client blocks**

Run: `rg -n "setBlock\(" src/main/java/com/jedts/theeconomist/client`

Expected: no matches in blueprint client code.

- [ ] **Step 8: Run targeting tests and the full suite**

Run: `.\gradlew.bat test --tests '*BlueprintTargetingTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: entire suite PASS.

- [ ] **Step 9: Commit virtual editing and rendering**

```powershell
git add src/main/java/com/jedts/theeconomist/client src/test/java/com/jedts/theeconomist/client
git commit -m "feat: render virtual blueprint drafts"
```

### Task 5: Blueprint item controls, rotation key, HUD, and cleanup hooks

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintClientKeys.java`
- Create: `src/main/java/com/jedts/theeconomist/client/BlueprintHudOverlay.java`
- Modify: `src/main/java/com/jedts/theeconomist/blueprint/EmptyBlueprintItem.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/TheEconomistClient.java`
- Modify: `src/main/resources/assets/theeconomist/lang/en_us.json`
- Delete: `src/main/java/com/jedts/theeconomist/client/BlueprintPlanningScreen.java`
- Modify: `src/test/java/com/jedts/theeconomist/blueprint/BlueprintItemBehaviorTest.java`

**Interfaces:**
- Consumes: the Task 3 controller API and Fabric client lifecycle, keybinding, rendering, networking, use, and attack callbacks.
- Produces: one authoritative item-use path, `R` rotation during placement, persistent control text, and deterministic cleanup hooks.

- [ ] **Step 1: Extend the item behavior test for active-session precedence**

```java
@Test
void active_session_actions_take_precedence_over_stack_state() {
    assertEquals(BlueprintItemAction.SAVE_DESIGN,
            BlueprintItemBehavior.action(BlueprintState.EMPTY, BlueprintSessionMode.DESIGN));
    assertEquals(BlueprintItemAction.CONFIRM_PLACEMENT,
            BlueprintItemBehavior.action(BlueprintState.DESIGNED, BlueprintSessionMode.PLACEMENT));
    assertEquals(BlueprintItemAction.DESIGN,
            BlueprintItemBehavior.action(BlueprintState.EMPTY, BlueprintSessionMode.NONE));
}
```

Keep `BlueprintSessionMode` in `com.jedts.theeconomist.blueprint` so common item behavior remains free of client-only class-loading.

- [ ] **Step 2: Run the behavior test and verify RED**

Run: `.\gradlew.bat test --tests '*BlueprintItemBehaviorTest'`

Expected: compilation fails because the two-argument action method and active actions do not exist.

- [ ] **Step 3: Route blueprint use through one controller entry point**

```java
public enum BlueprintItemAction { DESIGN, PLACE, SAVE_DESIGN, CONFIRM_PLACEMENT, NONE }

public static BlueprintItemAction action(BlueprintState state, BlueprintSessionMode mode) {
    if (mode == BlueprintSessionMode.DESIGN) return BlueprintItemAction.SAVE_DESIGN;
    if (mode == BlueprintSessionMode.PLACEMENT) return BlueprintItemAction.CONFIRM_PLACEMENT;
    return switch (state) {
        case EMPTY -> BlueprintItemAction.DESIGN;
        case DESIGNED -> BlueprintItemAction.PLACE;
        case PLANNED -> BlueprintItemAction.NONE;
    };
}

@Override
public InteractionResult use(Level level, Player player, InteractionHand hand) {
    if (!level.isClientSide()) return InteractionResult.SUCCESS;
    try {
        Class<?> controller = Class.forName("com.jedts.theeconomist.client.BlueprintClientController");
        controller.getMethod("handleBlueprintUse", ItemStack.class).invoke(null, player.getItemInHand(hand));
    } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Blueprint client controller is unavailable", exception);
    }
    return InteractionResult.SUCCESS;
}
```

Remove the blueprint-specific `UseItemCallback` branch from `TheEconomistClient`; this prevents one click from both starting and immediately completing a session.

- [ ] **Step 4: Register an `R` key mapping and placement HUD copy**

```java
public final class BlueprintClientKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "blueprint"));
    public static final KeyMapping ROTATE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.theeconomist.rotate_blueprint", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY));

    public static void tick() {
        while (ROTATE.consumeClick()) {
            if (BlueprintClientController.placing()) BlueprintClientController.rotatePlacement();
        }
    }
}

public final class BlueprintHudOverlay {
    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "blueprint_controls");
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, id,
                BlueprintHudOverlay::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!BlueprintClientController.placing()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int x = minecraft.getWindow().getGuiScaledWidth() / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 72;
        graphics.centeredText(minecraft.font, Component.translatable("hud.theeconomist.blueprint.rotate"),
                x, y, 0xFFFFFFFF);
    }
}
```

Call `BlueprintHudOverlay.register()` once from `TheEconomistClient.onInitializeClient()`.

- [ ] **Step 5: Replace old callbacks with session-aware editing and lifecycle cleanup**

```java
UseItemCallback.EVENT.register((player, level, hand) -> {
    if (!level.isClientSide() || !BlueprintClientController.designing()) return InteractionResult.PASS;
    ItemStack stack = player.getItemInHand(hand);
    return stack.getItem() instanceof BlockItem blockItem
            ? BlueprintClientController.useBlock(blockItem)
            : InteractionResult.PASS;
});

ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
    if (!BlueprintClientController.designing()) return false;
    return BlueprintClientController.attack() == InteractionResult.SUCCESS;
});

ClientTickEvents.END_CLIENT_TICK.register(client -> {
    BlueprintClientKeys.tick();
    BlueprintClientController.tick(client);
});

ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlueprintClientController.cancel());
```

Implement the controller's context and Escape checks explicitly:

```java
private static boolean escapeWasDown;

public static void tick(Minecraft client) {
    if (session.mode() == BlueprintSessionMode.NONE) return;
    boolean escapeDown = InputConstants.isKeyDown(InputConstants.KEY_ESCAPE);
    boolean disconnected = client.level == null || client.player == null;
    boolean alive = !disconnected && client.player.isAlive();
    boolean sameDimension = !disconnected
            && client.level.dimension().identifier().toString().equals(startingDimension);
    boolean hasBlueprint = !disconnected
            && blueprintSlot >= 0
            && client.player.getInventory().getItem(blueprintSlot).getItem() == BlueprintItems.EMPTY_BLUEPRINT;
    if ((!escapeWasDown && escapeDown)
            || session.shouldCancel(alive, sameDimension, hasBlueprint, disconnected)) {
        cancel();
    } else if (designing()) {
        soulCamera.tick(client);
    } else {
        refreshPlacementRenderMap();
    }
    escapeWasDown = escapeDown;
}
```

- [ ] **Step 6: Add localization strings**

```json
{
  "key.categories.theeconomist.blueprint": "Blueprint",
  "key.theeconomist.rotate_blueprint": "Rotate Blueprint",
  "hud.theeconomist.blueprint.rotate": "R to rotate"
}
```

Merge these keys into the existing `en_us.json` object without removing existing currency, citizen, contract, or blueprint item translations.

- [ ] **Step 7: Delete the unused planning screen and compile**

Run: `.\gradlew.bat compileJava`

Expected: PASS; no reference to `BlueprintPlanningScreen` remains.

- [ ] **Step 8: Run behavior tests and full tests**

Run: `.\gradlew.bat test --tests '*BlueprintItemBehaviorTest'`

Expected: PASS.

Run: `.\gradlew.bat test`

Expected: entire suite PASS.

- [ ] **Step 9: Commit controls and cleanup wiring**

```powershell
git add src/main/java/com/jedts/theeconomist/blueprint/EmptyBlueprintItem.java src/main/java/com/jedts/theeconomist/blueprint/BlueprintItemAction.java src/main/java/com/jedts/theeconomist/blueprint/BlueprintItemBehavior.java src/main/java/com/jedts/theeconomist/client src/main/resources/assets/theeconomist/lang/en_us.json src/test/java/com/jedts/theeconomist/blueprint/BlueprintItemBehaviorTest.java
git commit -m "feat: wire blueprint controls and cleanup"
```

### Task 6: Regression proof, README accuracy, and manual multiplayer checklist

**Files:**
- Modify: `README.md:264-309`
- Create: `docs/verification/blueprint-reimplementation-checklist.md`

**Interfaces:**
- Consumes: all preceding tasks and the approved design specification.
- Produces: an accurate user-facing description, recorded automated results, and a reproducible multiplayer verification checklist.

- [ ] **Step 1: Run blueprint-focused tests from a clean test execution**

Run: `.\gradlew.bat cleanTest test --tests '*Blueprint*' --rerun-tasks`

Expected: every blueprint test PASS; no test is reported UP-TO-DATE.

- [ ] **Step 2: Run the complete test suite and production build**

Run: `.\gradlew.bat test --rerun-tasks`

Expected: entire suite PASS.

Run: `.\gradlew.bat build`

Expected: `BUILD SUCCESSFUL` with no compilation, test, or resource-processing failure.

- [ ] **Step 3: Prove forbidden old behavior is absent**

Run: `rg -n "setInvisible\(true\)|getAbilities\(\)\.mayfly|level\.setBlock|32-block limit|isSprinting\(\)" src/main/java/com/jedts/theeconomist/client src/main/java/com/jedts/theeconomist/blueprint`

Expected: no blueprint implementation matches.

- [ ] **Step 4: Update the README implementation-status paragraph**

Replace the stale statement that interactive blueprint data is saved client-side with:

```markdown
Blueprint previews and design drafts exist only on the local client and never replace world blocks. Saving a design or confirming a placement sends a bounded proposal to the server, which validates the held item, lifecycle state, block palette, dimension, and destination before changing the blueprint item.
```

Preserve the user's documented vulnerable-body ghost mode, blue transparent blocks, `R` rotation hint, crosshair following, and right-click confirmation wording.

- [ ] **Step 5: Write the manual verification checklist with exact expected outcomes**

```markdown
# Blueprint Reimplementation Verification

- [ ] Start a two-player development server and give player A an Empty Blueprint plus stone and directional blocks.
- [ ] Player A enters design mode; player B sees A's body remain at the activation position.
- [ ] Player B damages A; A loses health and design mode exits cleanly on death.
- [ ] Player A re-enters design mode and flies the soul viewpoint with movement and vertical controls while the body does not move.
- [ ] Player A places, replaces, and removes private fake blocks; player B sees none of them and the real world remains unchanged.
- [ ] Stairs, slabs, and directional blocks render with their saved models/states, blue tint, and 50 percent opacity.
- [ ] Saving changes the held item to Designed only after the server accepts it.
- [ ] Placement follows the crosshair; pressing R rotates exactly 90 degrees and old preview positions disappear.
- [ ] Occupying a destination before confirmation makes the server reject the placement and the item remains Designed.
- [ ] A valid right-click confirmation changes the item to Planned with the correct dimension, origin, and rotation.
- [ ] Escape, blueprint loss, dimension change, disconnect, and death each restore the normal camera/input and clear previews.
```

- [ ] **Step 6: Inspect the final diff and working tree**

Run: `git diff --check`

Expected: no whitespace errors in files changed by this implementation.

Run: `git status --short`

Expected: only intended blueprint, README, verification, test, and resource changes remain; unrelated user changes are preserved.

- [ ] **Step 7: Commit documentation and verification evidence**

```powershell
git add README.md docs/verification/blueprint-reimplementation-checklist.md
git commit -m "docs: record blueprint verification"
```
