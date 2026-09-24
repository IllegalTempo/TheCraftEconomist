# Secure Player Trade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-authoritative player-to-player trade request HUD and bilateral escrow screen that completes exchanges safely after two acceptances, two readiness confirmations, and a three-second countdown.

**Architecture:** Model request and session transitions as pure Java state machines, keep all offered `ItemStack`s in server-owned persistent escrow, and expose only validated intent packets to clients. Fabric adapters handle player interaction, menus, lifecycle events, networking, HUD rendering, and restart recovery around the tested domain core.

**Tech Stack:** Java 25, Minecraft 26.3 Mojang mappings, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, JUnit Jupiter 5.13.4, Fabric GameTest

**Spec:** `docs/superpowers/specs/2026-09-19-citizen-secure-trade-foundation-design.md`

**Depends on:** `docs/superpowers/plans/2026-09-19-currency-foundation.md` for `CrownValues.total(Iterable<ItemStack>)`; the Citizen plan is independently shippable and is not required for player-to-player trade.

## Global Constraints

- A request begins only when a player shift-right-clicks another player with an empty main hand.
- Each player participates in at most one active request or trade session.
- Requests expire after exactly 15 seconds and require both independent acceptances.
- Request and active-session range is at most 16 blocks in the same dimension.
- The bottom-right HUD shows two player heads and two acceptance ticks; defaults are `R` accept and `X` decline, both rebindable.
- Decline or server cancellation fades the request HUD over 250 milliseconds on both clients.
- Each participant has exactly 18 public offer slots arranged 6 by 3; the remote grid is read-only.
- Any accepted offer edit clears both readiness flags and resets a running countdown.
- Both ready flags start a 60-server-tick countdown; completion is server-authoritative and idempotent.
- Death, disconnect, dimension change, excess distance, screen close, decline, timeout, or invalid state cancels; nonlethal damage does not.
- Successful overflow is dropped at the receiving player's feet; there is no mailbox.
- Cancellation returns every escrowed stack to its original owner, immediately when safe or on next login when offline.
- No packet, exception, restart, or repeated recovery pass may silently delete an escrowed item.
- Common code must remain safe on a dedicated server.

## Review Focus

- Two players can initiate toward each other in the same tick; Task 2 tests canonical pair locking so only one request survives.
- Quick-move, drag, number-key, double-click, and creative clone actions can bypass naive slot checks; Task 6 tests every menu action against owned and remote slots.
- A crash between item insertion and delivery-marker persistence can duplicate or lose items; Task 4 uses delivery tokens plus a durable journal and tests every interruption boundary.
- Dimension/range changes can race the final countdown tick; Task 7 revalidates immediately before `COMMITTING` and tests cancellation wins.
- Overflow entities can be duplicated during recovery or picked up by the wrong player; Task 4 tests unique delivery tokens, ownership, pickup delay, and recovery scanning.

---

### Task 1: Define trade identifiers, clocks, and participant pairs

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/domain/TradePair.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/domain/TradeClock.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/domain/TradeRules.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/domain/TradePairTest.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/domain/TradeRulesTest.java`

**Interfaces:**
- Consumes: player UUIDs and an injected monotonic server tick.
- Produces: canonical `TradePair`, `TradeClock.nowTick(): long`, and named constants `REQUEST_TIMEOUT_TICKS = 300`, `MAX_DISTANCE_SQUARED = 256.0`, `COUNTDOWN_TICKS = 60`, and `OFFER_SLOTS = 18`.

- [ ] **Step 1: Write canonical-pair and constant tests**

```java
@Test
void pairOrderDoesNotDependOnWhoInitiated() {
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    assertEquals(TradePair.of(first, second), TradePair.of(second, first));
    assertThrows(IllegalArgumentException.class, () -> TradePair.of(first, first));
}

@Test
void approvedRulesArePinned() {
    assertEquals(300L, TradeRules.REQUEST_TIMEOUT_TICKS);
    assertEquals(256.0, TradeRules.MAX_DISTANCE_SQUARED);
    assertEquals(60, TradeRules.COUNTDOWN_TICKS);
    assertEquals(18, TradeRules.OFFER_SLOTS);
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.domain.TradePairTest" --tests "com.jedts.theeconomist.trade.domain.TradeRulesTest"`

Expected: test compilation fails because the domain types do not exist.

- [ ] **Step 3: Implement canonical ordering and constants**

`TradePair.of` compares UUIDs by unsigned most-significant bits, then unsigned least-significant bits, and stores the lower value first. It rejects null and equal participants. `TradeRules` has a private constructor and the exact constants above. `TradeClock` is a functional interface so tests never read wall-clock time.

- [ ] **Step 4: Run the domain tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.domain.*"`

Expected: all focused tests pass.

- [ ] **Step 5: Commit shared trade domain primitives**

```powershell
git add src/main/java/com/jedts/theeconomist/trade/domain src/test/java/com/jedts/theeconomist/trade/domain
git commit -m "feat: define secure trade rules"
```

### Task 2: Implement the request state machine and one-trade-per-player locking

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/request/TradeRequestStatus.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/request/TradeRequest.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/request/TradeRequestResult.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/request/TradeRequestRegistry.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/request/TradeRequestTest.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/request/TradeRequestRegistryTest.java`

**Interfaces:**
- Consumes: canonical `TradePair`, request UUID, creation/expiry ticks, and authenticated participant UUID actions.
- Produces: immutable request snapshots, `accept`, `decline`, `expire`, and an atomic registry keyed by both pair and participant.

- [ ] **Step 1: Write request transition tests**

Use fixed ticks and UUIDs to prove initial flags are false, each participant can accept only their own flag, duplicate acceptance is idempotent, the second acceptance yields `READY_TO_OPEN`, a nonparticipant is rejected, decline is terminal, and tick 300 expires while tick 299 does not.

```java
TradeRequest request = TradeRequest.create(requestId, initiator, recipient, 1_000L);
assertFalse(request.accepted(initiator));
assertEquals(TradeRequestResult.WAITING, request.accept(initiator, 1_010L));
assertEquals(TradeRequestResult.READY_TO_OPEN, request.accept(recipient, 1_011L));
assertEquals(TradeRequestStatus.ACCEPTED, request.status());
```

- [ ] **Step 2: Run the request test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.request.TradeRequestTest"`

Expected: test compilation fails because request types do not exist.

- [ ] **Step 3: Implement explicit terminal transitions**

`TradeRequest` stores request ID, pair, initiator, `expiresAtTick`, two acceptance booleans, status, and terminal reason. Mutators are package-private and synchronized through the registry. Terminal requests ignore repeated actions without changing their original result.

- [ ] **Step 4: Write registry race and locking tests**

Test two concurrent `createOrReuse(A, B)` calls with reversed initiators using an executor and barrier. Assert both receive the same request UUID. Test A-B blocks A-C and C-B, terminal removal releases both participants, and a duplicate same-pair interaction returns the existing request without resetting acceptance or expiry.

- [ ] **Step 5: Implement atomic registry indexes**

Use one lock protecting `Map<UUID, TradeRequest> byId`, `Map<TradePair, UUID> byPair`, and `Map<UUID, UUID> byParticipant`. Validate all conflicts before writing any index. Removal checks that each index still points to the same request ID before deleting it.

- [ ] **Step 6: Run request tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.request.*"`

Expected: all transition, timeout, conflict, and concurrent-pair tests pass.

- [ ] **Step 7: Commit request domain**

```powershell
git add src/main/java/com/jedts/theeconomist/trade/request src/test/java/com/jedts/theeconomist/trade/request
git commit -m "feat: add bilateral trade requests"
```

### Task 3: Implement session readiness and countdown state

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/session/TradeSessionStatus.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/session/TradeCancelReason.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/session/TradeSession.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/session/TradeSessionSnapshot.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/session/TradeSessionTest.java`

**Interfaces:**
- Consumes: an accepted request, server tick, participant UUID, offer revision, and readiness/cancel actions.
- Produces: states `OPEN`, `COUNTDOWN`, `COMMITTING`, `COMPLETED`, `CANCELLING`, `CANCELLED`; `editOffer`, `setReady`, `tick`, `beginCancel`, and immutable snapshots.

- [ ] **Step 1: Write the full transition matrix as tests**

Cover these exact paths:

```text
OPEN + first ready -> OPEN
OPEN + second ready -> COUNTDOWN(deadline = now + 60)
COUNTDOWN + accepted edit -> OPEN and both not ready
COUNTDOWN + tick before deadline -> COUNTDOWN
COUNTDOWN + tick at deadline -> COMMITTING
OPEN/COUNTDOWN + cancel -> CANCELLING
COMMITTING + finish -> COMPLETED
CANCELLING + finish -> CANCELLED
terminal + repeated action -> unchanged
```

Also test a nonparticipant cannot edit, ready, or cancel; offer slot indices -1 and 18 fail; a duplicate edit revision is idempotent; and a stale lower revision is rejected.

- [ ] **Step 2: Run the session test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.session.TradeSessionTest"`

Expected: test compilation fails because session types do not exist.

- [ ] **Step 3: Implement the synchronized state machine**

Store readiness per participant, `long offerRevision`, optional countdown deadline, state, and cancel reason. Only an accepted edit with a strictly greater revision changes the state. `tick(now)` returns a transition result; it never performs inventory I/O. `remainingTicks(now)` clamps to `0..60`.

Use an immutable snapshot record containing IDs, participant pair, state, both readiness flags, offer revision, and remaining ticks. Clients never receive mutable session objects.

- [ ] **Step 4: Run session tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.session.*"`

Expected: all state and idempotency tests pass.

- [ ] **Step 5: Commit session state**

```powershell
git add src/main/java/com/jedts/theeconomist/trade/session src/test/java/com/jedts/theeconomist/trade/session
git commit -m "feat: add trade readiness countdown"
```

### Task 4: Build persistent escrow and idempotent delivery recovery

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/EscrowEntry.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/EscrowOffer.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/DeliveryDirection.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/DeliveryState.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/DeliveryMarker.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/PersistedTradeSession.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/TradeLedger.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/TradeLedgerSavedData.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/persistence/TradeLedgerCodec.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/delivery/DeliveryToken.java`
- Create: `src/main/java/com/jedts/theeconomist/trade/delivery/TradeDeliveryService.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/persistence/TradeLedgerCodecTest.java`
- Create: `src/test/java/com/jedts/theeconomist/trade/delivery/TradeDeliveryServiceTest.java`

**Interfaces:**
- Consumes: authoritative `ItemStack`s removed from a player's inventory, session/entry UUIDs, original owner, intended recipient, and a storage/delivery adapter.
- Produces: 18-slot persistent offers, versioned saved data, per-entry delivery markers, and idempotent deliver-or-return operations.

- [ ] **Step 1: Write escrow codec round-trip tests**

Build a persisted session with both offers, empty slots between stacks, custom item components, all session states, and delivery markers. Encode to `CompoundTag`, decode, and assert deep stack equality with `ItemStack.matches` plus exact IDs, owners, slot indexes, counts, states, and schema version. Reject duplicate entry UUIDs, duplicate occupied offer slots, invalid slot 18, nonpositive counts, and unsupported schema versions.

- [ ] **Step 2: Run the codec test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.persistence.TradeLedgerCodecTest"`

Expected: test compilation fails because persistence types do not exist.

- [ ] **Step 3: Implement immutable escrow records and versioned serialization**

An `EscrowEntry` contains `entryId`, `sessionId`, `originalOwner`, `offerSlot`, and a defensive `ItemStack.copy()`. `EscrowOffer` owns exactly 18 optional entries and exposes copies. Use the supported 26.3 `ItemStack` codec with the world registry provider; do not hand-serialize only ID/count because components and modded items must survive.

`TradeLedgerSavedData` marks itself dirty after every accepted deposit, withdrawal, session transition, or delivery-marker change. It never removes an entry until its terminal delivery/return marker is durable.

- [ ] **Step 4: Define the delivery-token protocol**

Register an internal non-user-facing item data component `theeconomist:trade_delivery_token` containing transaction UUID, entry UUID, recipient UUID, and direction (`EXCHANGE` or `RETURN`). A delivered stack or overflow entity retains this token until the ledger records `DELIVERED` and a later cleanup pass sees the terminal transaction. Token data is not used for value and cannot authorize a packet.

- [ ] **Step 5: Write interruption-boundary delivery tests**

Use an in-memory adapter with injectable failure points:

```text
before PREPARED marker
after PREPARED marker, before inventory insertion
after inventory insertion, before player save
after player save, before DELIVERED marker
after DELIVERED marker, before escrow deletion
after overflow spawn, before world/chunk persistence
```

Restart the service after each injected failure. Assert the recipient ends with exactly one copy, the original owner receives exactly one copy on cancellation, no entry disappears, and repeated recovery reaches the same terminal result. Add a full-inventory case that asserts an owned overflow drop with the same delivery token and no duplicate second drop.

- [ ] **Step 6: Implement prepare-scan-deliver-mark recovery**

For each entry:

1. Persist `PREPARED` in the journal.
2. Scan the intended player's inventory and owned nearby item entities for the exact token.
3. If found, persist `DELIVERED` without adding another stack.
4. If absent and the player is available, attach the token, fill inventory, drop remainder at that player's feet with owner UUID and pickup protection, then explicitly request player/chunk persistence.
5. Persist `DELIVERED` and only then make the escrow entry eligible for cleanup.
6. If the player is offline, retain `PREPARED` escrow for the next login; do not create a mailbox or discard it.

The cancellation direction targets `originalOwner`; successful exchange targets the other participant. The service validates this mapping from the persisted pair rather than accepting an arbitrary recipient.

- [ ] **Step 7: Run persistence and delivery tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.trade.persistence.*" --tests "com.jedts.theeconomist.trade.delivery.*"`

Expected: all round-trip, corruption, overflow, and interruption-boundary tests pass.

- [ ] **Step 8: Commit persistent escrow**

```powershell
git add src/main/java/com/jedts/theeconomist/trade/persistence src/main/java/com/jedts/theeconomist/trade/delivery src/test/java/com/jedts/theeconomist/trade/persistence src/test/java/com/jedts/theeconomist/trade/delivery
git commit -m "feat: add persistent trade escrow"
```
