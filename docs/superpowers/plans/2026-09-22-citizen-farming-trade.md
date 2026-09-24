# Citizen Farming and Crown Trade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Citizens farm real wheat on claimed land, sell it to players for physical Crowns, buy farming inputs with earned Crowns, and respond to deliberate land challenges.

**Architecture:** A dimension-scoped claim registry records owner per farmland block and derives same-owner connected plots. A bounded server work loop owns Citizen farming and inventory; separate pure services calculate price snapshots, challenge probability, and physical coin movement. Server-authoritative packets connect immediate trade actions to the existing Citizen screen.

**Tech Stack:** Java 25, Minecraft 26.3 Mojang mappings, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Gradle 9.6, JUnit Jupiter 5.13.4, Fabric GameTest.

**Spec:** `docs/superpowers/specs/2026-09-22-citizen-farming-trade-design.md`

## Global Constraints

- A plot is a maximal four-way-connected group of farmland blocks with the same owner; adjacent different owners remain separate.
- Claims record ownership but do not block a player from hoeing, planting, or harvesting.
- A Citizen starts with one wooden hoe and gathers actual wheat seeds and wheat drops. Farming touches loaded chunks only.
- Wheat price snapshots update every 6,000 game ticks, not for each transaction.
- Coins stay as Copper (1), Silver (10), and Gold (100) Crown items; payment and change come from actual inventories.
- A player must shift-use a hoe on Citizen-owned farmland to initiate player-versus-Citizen land combat. Combat never transfers claims.
- Trade packets carry intent, quantity, quote version, and request ID, never authoritative price or item stacks.
- The existing player-to-player trade flow is separate. Its incomplete crash recovery is not expanded by this plan.
- A process crash between vanilla player/entity saves is outside this milestone's durability guarantee.
- This workspace currently has no `.git` directory. The commit steps below apply if a repository is restored before execution; otherwise record each task's passing verification and continue without creating a new repository.
- The Gradle wrapper attempted a Gradle 9.6 download during plan review and was blocked by this environment's network policy. Resolve the wrapper/runtime access before treating any test command as verified.

## File map

| Unit | Files | Responsibility |
| --- | --- | --- |
| Claims | `citizen/farm/claim/PlotClaims.java`, `PlotOwner.java`, `PlotClaimSavedData.java`, `PlotClaimService.java` | Ownership map, same-owner topology, saved data, world callbacks |
| Inventory | `citizen/farm/CitizenFarmInventory.java`, `citizen/entity/CitizenEntity.java` | Finite item storage, save/load, starting hoe, death drops |
| Work | `citizen/farm/FarmerWorkPlanner.java`, `FarmerWorkService.java` | Bounded target selection and real hoe/grass/plant/harvest actions |
| Prices | `citizen/trade/CitizenPriceSnapshot.java`, `CitizenPricing.java` | 6,000-tick snapshot and wheat/input quotes |
| Challenges | `citizen/farm/conflict/LandConflictRules.java`, `LandConflictService.java` | One decision per challenge, cooldown, timed combat |
| Payment | `citizen/trade/CoinStock.java`, `CoinPlan.java`, `CoinExchange.java`, `CitizenTradeAction.java`, `CitizenTradeService.java` | Exact physical coin plan, preflight, atomic in-tick transfer |
| Networking and UI | `citizen/trade/CitizenTradeActionPayload.java`, `CitizenTradeViewPayload.java`, `CitizenTradeNetworking.java`, `client/CitizenTradeScreen.java` | Bounded C2S/S2C messages and rendered controls |
| Registration | `citizen/CitizenModule.java`, `client/TheEconomistClient.java`, `client/CitizenInfoScreen.java` | Server and client hooks and Trade button |

## Review Focus

1. Two owners hoe fields until they touch: neither owner's farmland or plot ID changes; Task 1 tests it.
2. A crop is harvested after its chunk unloads or block changes: no action runs and no inventory item appears; Task 3 tests it.
3. A player repeats a trade packet or changes coins between quote and click: only one transaction can occur; Task 6 tests it.
4. A large denomination needs change that the recipient lacks: the exchange is refused without removing goods or coins; Task 5 tests it.
5. A player harvests Citizen-owned wheat normally: the action remains permitted and does not trigger combat; Task 8 tests it in-world.

---

### Task 1: Claim ownership and same-owner plot topology

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/claim/PlotOwner.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/claim/PlotClaims.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/farm/claim/PlotClaimsTest.java`

**Interfaces:**
- Consumes: `BlockPos`, owner type (`PLAYER` or `CITIZEN`), owner UUID.
- Produces: `PlotClaims.ownerAt(BlockPos): Optional<PlotOwner>`, `claimUnclaimedComponent(BlockPos, PlotOwner, Predicate<BlockPos>): int`, `claimNewFarmland(BlockPos, PlotOwner): boolean`, `remove(BlockPos): void`, `release(PlotOwner): int`, `component(BlockPos): Set<BlockPos>`.

- [ ] **Step 1: Write failing topology tests.** Pin four-way adjacency, diagonal separation, same-owner merge/split, component claiming, and different-owner touching. The critical assertion is:

```java
claims.claimNewFarmland(new BlockPos(0, 64, 0), playerA);
claims.claimNewFarmland(new BlockPos(1, 64, 0), playerB);
assertEquals(Set.of(new BlockPos(0, 64, 0)), claims.component(new BlockPos(0, 64, 0)));
assertEquals(playerB, claims.ownerAt(new BlockPos(1, 64, 0)).orElseThrow());
```

- [ ] **Step 2: Run the focused test; expect compilation failure because `PlotClaims` does not exist.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.claim.PlotClaimsTest'
```

- [ ] **Step 3: Implement the ownership map.** `PlotOwner` is a record of enum `Kind` and UUID. `PlotClaims` stores `Map<BlockPos, PlotOwner>`. `component` searches north/south/east/west neighbors, visiting only the same owner. Cap a single claim query at 4,096 loaded farmland blocks and refuse the claim if a connected component exceeds that cap; do not partially claim it. `claimUnclaimedComponent` claims only blocks accepted by its farmland predicate and currently unowned. A new farmland block gets the actor's owner without touching adjacent owners.

```java
public record PlotOwner(Kind kind, UUID id) {
    public enum Kind { PLAYER, CITIZEN }
    public PlotOwner { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
}
```

- [ ] **Step 4: Run focused tests, then the full unit suite. Expect all pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.claim.PlotClaimsTest'
.\gradlew.bat test
```

- [ ] **Step 5: Commit this reviewable unit if Git metadata is available.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/farm/claim src/test/java/com/jedts/theeconomist/citizen/farm/claim
git commit -m "feat: model farmland claims by owner"
```

### Task 2: Persist claims and register world updates

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/claim/PlotClaimSavedData.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/claim/PlotClaimService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/farm/claim/PlotClaimSavedDataTest.java`

**Interfaces:**
- Consumes: Task 1 ownership map, Fabric block-use/break and server-tick events, `ServerLevel`.
- Produces: `PlotClaimService.forLevel(ServerLevel): PlotClaimService`, `ownerAt(BlockPos)`, `claimPlayerUse(ServerPlayer, BlockPos)`, `claimCitizen(CitizenEntity, BlockPos)`, `releaseCitizen(UUID)`, `reconcileLoaded(int budget)`.

- [ ] **Step 1: Write failing save tests.** Round-trip player/Citizen owners across two adjacent blocks, then load and prove they remain separate. Include removal of a farmland block and `releaseCitizen` leaving the player claim intact.

```java
assertEquals(playerOwner, restored.ownerAt(new BlockPos(0, 64, 0)).orElseThrow());
assertEquals(citizenOwner, restored.ownerAt(new BlockPos(1, 64, 0)).orElseThrow());
```

- [ ] **Step 2: Run the focused test; expect missing storage classes.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.claim.PlotClaimSavedDataTest'
```

- [ ] **Step 3: Implement the adapter.** Follow `CitizenContractSavedData`'s Codec-backed `SavedDataType` pattern. Serialize dimension-local `(x,y,z,kind,uuid)` entries. `PlotClaimService` loads with `level.getDataStorage().computeIfAbsent(TYPE)`, calls `setDirty()` after each change, handles hoe use on dirt/grass and existing unclaimed farmland, and removes records when a loaded position stops being farmland. Limit reconciliation to 128 loaded records per server tick. Register server hooks from `CitizenModule`; do not intercept normal player farming when the block belongs to someone else.

```java
public static PlotClaimService forLevel(ServerLevel level) {
    return SERVICES.computeIfAbsent(level, PlotClaimService::new);
}
```

- [ ] **Step 4: Run focused tests and `test`; expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.claim.PlotClaimSavedDataTest'
.\gradlew.bat test
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/farm/claim src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java src/test/java/com/jedts/theeconomist/citizen/farm/claim
git commit -m "feat: persist and update farm claims"
```

### Task 3: Give Citizens persistent inventory and a bounded wheat work loop

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/CitizenFarmInventory.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/FarmerWorkPlanner.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/FarmerWorkService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/farm/CitizenFarmInventoryTest.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/farm/FarmerWorkPlannerTest.java`

**Interfaces:**
- Consumes: Task 2 `PlotClaimService`, Minecraft block loot and crop state, entity `ValueInput`/`ValueOutput`.
- Produces: `CitizenEntity.farmInventory(): CitizenFarmInventory`, `CitizenFarmInventory.count(Item): int`, `CitizenFarmInventory.sellableWheat(): int`, `FarmerWorkService.tick(CitizenEntity): void`, `FarmerWorkPlanner.choose(List<WorkTarget>): Optional<WorkTarget>`. `FarmerWorkPlanner` defines `Action { HARVEST, PLANT, GATHER_SEEDS, HOE }` and `WorkTarget(BlockPos pos, Action action, boolean loaded, boolean reachable)`.

- [ ] **Step 1: Write failing inventory/planner tests.** Assert 27 finite slots, no insertion loss on full inventory, seed reserve equal to empty owned farmland count, owned ripe wheat before new hoe targets, and no target when all candidates are unloaded or unreachable.

```java
assertTrue(planner.choose(List.of(new WorkTarget(unloadedPos, Action.HARVEST, false, true))).isEmpty());
CitizenFarmInventory inventory = new CitizenFarmInventory();
inventory.insert(new ItemStack(Items.WHEAT, 8));
assertEquals(4, inventory.sellableWheat());
```

- [ ] **Step 2: Run focused tests; expect missing classes.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.*'
```

- [ ] **Step 3: Implement inventory, persistence, and real work actions.** Use 27 `ItemStack` slots, copying on insertion, component-preserving serialization, and one starter wooden hoe only when the entity is newly created. `CitizenEntity` saves/loads inventory and work fields in separate named children, drops every remaining stack once on death, and releases claims through Task 2's service. The work loop acts at most once per 20 ticks, searches loaded candidates within 16 blocks, and validates the block and reach again immediately before action. Grass seed gathering uses normal drops; hoeing consumes durability; planting removes one seed; harvesting mature wheat adds normal loot and leaves the block ready for replanting. Never create crop items as a timer reward.

```java
public Optional<WorkTarget> choose(List<WorkTarget> candidates) {
    return candidates.stream().filter(WorkTarget::loaded).filter(WorkTarget::reachable)
            .min(Comparator.comparingInt(target -> priority(target.action())));
}
```

For the world adapter, use the mapped crop age and block loot methods from this Minecraft version; preserve actual drops before clearing a mature crop. Avoid modifying `CitizenEntity`'s existing identity, stats, skill, job, or info-screen persistence semantics.

- [ ] **Step 4: Run focused tests, full suite, and compile. Expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.farm.*'
.\gradlew.bat test compileJava
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/farm src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java src/test/java/com/jedts/theeconomist/citizen/farm
git commit -m "feat: let citizens farm and retain real harvests"
```

### Task 4: Price snapshots and deliberate land conflicts

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenPriceSnapshot.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenPricing.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/conflict/LandConflictRules.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/farm/conflict/LandConflictService.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/trade/CitizenPricingTest.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/farm/conflict/LandConflictRulesTest.java`

**Interfaces:**
- Consumes: Task 1 plot size, Task 2 claim service, Task 3 stock and anger, world game tick, attacker/defender health and armor.
- Produces: `CitizenPricing.update(CitizenPriceSnapshot, long, int): CitizenPriceSnapshot`, `LandConflictRules.fightChance(int anger, int plotBlocks, int unclaimedCount, double citizenHealth, double opponentHealth, double citizenArmor, double opponentArmor, int wheatPrice): double`, `LandConflictService.challenge(CitizenEntity, LivingEntity, BlockPos): boolean`. `CitizenPriceSnapshot` is `record CitizenPriceSnapshot(long calculatedAtTick, long version, int wheatPrice, int seedPrice, int hoePrice)`.

- [ ] **Step 1: Write failing price/conflict tests.** Pin no price change at tick 5,999, refresh at 6,000, stock-dependent price from 2 to 6, fixed seed/hoe input prices, bounded 5–95% fight probability, monotonic anger/value/scarcity, cooldown, and no combat from ordinary player harvest. Include:

```java
CitizenPriceSnapshot initial = new CitizenPriceSnapshot(0, 1, 4, 1, 8);
assertEquals(initial, CitizenPricing.update(initial, 5_999, 16));
assertEquals(2, CitizenPricing.update(initial, 6_000, 32).wheatPrice());
assertTrue(LandConflictRules.fightChance(90, 16, 0, 20, 20, 0, 0, 4)
        > LandConflictRules.fightChance(10, 16, 0, 20, 20, 0, 0, 4));
```

- [ ] **Step 2: Run focused tests; expect missing classes.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenPricingTest' --tests 'com.jedts.theeconomist.citizen.farm.conflict.LandConflictRulesTest'
```

- [ ] **Step 3: Implement price and conflict services.** `CitizenPricing.update` returns its prior immutable snapshot until 6,000 game ticks have elapsed. Recompute `clamp(4 + ceil((16 - sellableWheat)/8), 2, 6)` and increment version once when overdue. Save the snapshot on the Citizen. `LandConflictRules` implements the spec's scarcity, threat, net-value, and probability formulas. `LandConflictService` handles shift-hoe challenges through the server use-block callback, refuses creative/spectator targets, stores `(citizen, opponent, plot)` cooldown until game tick +6,000, and runs a 200-tick maximum aggression goal that clears on range/death/dimension/low-health. Autonomous Citizen-versus-Citizen challenges run only after 6,000 ticks with no unclaimed work target. The callback returns `PASS` for ordinary harvesting and farming.

```java
public static CitizenPriceSnapshot update(CitizenPriceSnapshot old, long now, int stock) {
    if (now - old.calculatedAtTick() < 6_000L) return old;
    int wheat = Math.max(2, Math.min(6, 4 + (int) Math.ceil((16 - stock) / 8.0)));
    return new CitizenPriceSnapshot(now, old.version() + 1, wheat, 1, 8);
}
```

- [ ] **Step 4: Run focused and full tests. Expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenPricingTest' --tests 'com.jedts.theeconomist.citizen.farm.conflict.LandConflictRulesTest'
.\gradlew.bat test compileJava
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/trade src/main/java/com/jedts/theeconomist/citizen/farm/conflict src/main/java/com/jedts/theeconomist/citizen/entity/CitizenEntity.java src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java src/test/java/com/jedts/theeconomist/citizen/trade src/test/java/com/jedts/theeconomist/citizen/farm/conflict
git commit -m "feat: price citizen harvests and resolve land challenges"
```

### Task 5: Plan exact exchanges of physical Crowns

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CoinStock.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CoinPlan.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CoinExchange.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/trade/CoinExchangeTest.java`

**Interfaces:**
- Consumes: payer and receiver counts for Copper/Silver/Gold Crowns and positive integer price.
- Produces: `CoinExchange.plan(CoinStock payer, CoinStock receiver, int price): Optional<CoinPlan>`, where `CoinPlan` contains exact outgoing coin counts from each side and leaves total Crown value unchanged.

- [ ] **Step 1: Write failing payment tests.** Cover exact Copper payment, Silver overpayment with seven Copper change for a three-Crown price, no-change refusal, Gold denomination, equal-valued plans, and total count bounds. Include:

```java
assertTrue(CoinExchange.plan(new CoinStock(0, 1, 0), new CoinStock(7, 0, 0), 3).isPresent());
assertTrue(CoinExchange.plan(new CoinStock(0, 1, 0), new CoinStock(6, 0, 0), 3).isEmpty());
```

- [ ] **Step 2: Run focused test; expect missing types.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CoinExchangeTest'
```

- [ ] **Step 3: Implement bounded coin planning.** Define top-level immutable `CoinStock(int copper, int silver, int gold)` and `CoinPlan(CoinStock payment, CoinStock change)` with nonnegative validation and `long value()`. Enumerate possible outgoing Gold/Silver/Copper counts up to stock or needed payment plus maximum available change; choose the feasible plan with the least overpayment and fewest moved coins. Never create a denomination not in `payment` or `change` source stock. Reject price <= 0 and overflow with checked `long` arithmetic.

```java
if (payment.value() - change.value() != price) return Optional.empty();
if (!payer.contains(payment) || !receiver.contains(change)) return Optional.empty();
return Optional.of(new CoinPlan(payment, change));
```

- [ ] **Step 4: Run focused and full tests. Expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CoinExchangeTest'
.\gradlew.bat test
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/trade/CoinStock.java src/main/java/com/jedts/theeconomist/citizen/trade/CoinPlan.java src/main/java/com/jedts/theeconomist/citizen/trade/CoinExchange.java src/test/java/com/jedts/theeconomist/citizen/trade/CoinExchangeTest.java
git commit -m "feat: plan exact physical Crown payment and change"
```

### Task 6: Execute immediate, validated Citizen trades

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeAction.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeService.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeResult.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/trade/CitizenTradeServiceTest.java`

**Interfaces:**
- Consumes: Task 3 Citizen inventory, Task 4 snapshot, Task 5 coin plan, player inventory, action `BUY_WHEAT`, `SELL_SEEDS`, or `SELL_WOODEN_HOE`, quantity, quote version, request UUID.
- Produces: `CitizenTradeService.execute(ServerPlayer, CitizenEntity, CitizenTradeAction, int, long, UUID): CitizenTradeResult`. `CitizenTradeAction` contains `OPEN`, `CLOSE`, `BUY_WHEAT`, `SELL_SEEDS`, and `SELL_WOODEN_HOE`; `OPEN` and `CLOSE` are handled by networking, not passed to the transaction service. `CitizenTradeResult` contains `SUCCESS`, `DUPLICATE`, `STALE_QUOTE`, `INVALID_QUANTITY`, `OUT_OF_RANGE`, `INSUFFICIENT_GOODS`, `INSUFFICIENT_FUNDS`, `NO_CHANGE`, and `NO_CAPACITY`.

- [ ] **Step 1: Write failing transaction tests.** Pin ordinary wheat purchase, seed/hoe sale after the Citizen earns coins, no purchase from the four-wheat reserve, insufficient funds, missing change, full recipient inventory, stale quote, invalid quantity, out-of-range player, and duplicate request ID. For all refusals compare exact before/after inventory snapshots.

```java
CitizenTradeResult second = service.execute(player, citizen, BUY_WHEAT, 1, snapshot.version(), requestId);
assertEquals(CitizenTradeResult.DUPLICATE, second);
assertEquals(beforeSecondCall, captureBothInventories(player, citizen));
```

- [ ] **Step 2: Run focused test; expect missing service.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenTradeServiceTest'
```

- [ ] **Step 3: Implement preflight and in-tick commit.** Validate action/quantity and server context first. Recompute available goods, price multiplication with checked arithmetic, coin stocks, a `CoinExchange` plan, and capacity of both inventories including incoming goods and change. Do this on the server thread against copied inventory state, then apply the entire transfer before another packet can run. Mark the request ID consumed before responding; retain a bounded recent-ID set per player/Citizen pair until the screen closes. Refuse stale quote versions and return a refreshed view. Never trust client stacks or price.

```java
if (quoteVersion != citizen.priceSnapshot().version()) return CitizenTradeResult.STALE_QUOTE;
if (quantity < 1 || quantity > 64) return CitizenTradeResult.INVALID_QUANTITY;
if (player.distanceToSqr(citizen) > 36.0 || player.level() != citizen.level())
    return CitizenTradeResult.OUT_OF_RANGE;
```

- [ ] **Step 4: Run focused and full tests. Expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenTradeServiceTest'
.\gradlew.bat test compileJava
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeAction.java src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeService.java src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeResult.java src/test/java/com/jedts/theeconomist/citizen/trade/CitizenTradeServiceTest.java
git commit -m "feat: execute validated Citizen Crown trades"
```

### Task 7: Connect the Citizen screen to the server trade service

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeActionPayload.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeViewPayload.java`
- Create: `src/main/java/com/jedts/theeconomist/citizen/trade/CitizenTradeNetworking.java`
- Create: `src/main/java/com/jedts/theeconomist/client/CitizenTradeScreen.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/CitizenInfoScreen.java`
- Modify: `src/main/java/com/jedts/theeconomist/client/TheEconomistClient.java`
- Modify: `src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java`
- Test: `src/test/java/com/jedts/theeconomist/citizen/trade/CitizenTradePayloadTest.java`

**Interfaces:**
- Consumes: Task 6 service, existing `CitizenInfoPayload` and `CitizenInfoScreen`, Fabric payload registries.
- Produces: Trade button, quantity controls, stock/price/funds display, explicit refusal reasons, and bounded C2S/S2C packets.

- [ ] **Step 1: Write failing payload tests.** Round-trip maximum legal quantity and short explanation; reject negative quantity, oversized strings, unknown action, and invalid entity ID. Verify the view contains quote version, sellable wheat, current prices, and change availability rather than private Citizen inventory stacks.

```java
assertEquals(6, decoded.wheatPrice());
assertEquals(42L, decoded.quoteVersion());
assertEquals("Change unavailable", decoded.message());
```

- [ ] **Step 2: Run focused test; expect missing payloads.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenTradePayloadTest'
```

- [ ] **Step 3: Implement registration and UI.** Register `CitizenTradeActionPayload` serverbound and `CitizenTradeViewPayload` clientbound in `CitizenModule`. A Trade button on `CitizenInfoScreen` sends `OPEN` and displays `CitizenTradeScreen` after the server view arrives. The screen shows wheat stock and unit price, quantity controls, Buy wheat / Sell seeds / Sell hoe buttons, Crown value, change availability, and last server message. `TheEconomistClient` receives views on the client thread. On each click send a new UUID and last displayed quote version. `CitizenTradeNetworking` keeps the active Citizen entity ID by player UUID from `OPEN` until `CLOSE`, disconnect, death, dimension change, or distance failure. Server receiver resolves entity by ID, checks distance and active screen association, calls Task 6 only for buy/sell actions, then sends a fresh view. Closing the screen sends `CLOSE` and clears client-only state.

```java
ServerPlayNetworking.registerGlobalReceiver(CitizenTradeActionPayload.TYPE, (payload, context) ->
        context.server().execute(() -> CitizenTradeNetworking.handle(context.player(), payload)));
```

Create `CitizenTradeNetworking.java` in `citizen/trade` for that handler so the module stays registration-only. Keep the existing Citizen info display and player trade flow functional.

- [ ] **Step 4: Run payload tests and compile client/server. Expect pass.**

```powershell
.\gradlew.bat test --tests 'com.jedts.theeconomist.citizen.trade.CitizenTradePayloadTest'
.\gradlew.bat test classes
```

- [ ] **Step 5: Commit if the repository exists.**

```powershell
git add src/main/java/com/jedts/theeconomist/citizen/trade src/main/java/com/jedts/theeconomist/client/CitizenTradeScreen.java src/main/java/com/jedts/theeconomist/client/CitizenInfoScreen.java src/main/java/com/jedts/theeconomist/client/TheEconomistClient.java src/main/java/com/jedts/theeconomist/citizen/CitizenModule.java src/test/java/com/jedts/theeconomist/citizen/trade
git commit -m "feat: expose Citizen Crown trading in the UI"
```

### Task 8: Integrated world verification and user instructions

**Files:**
- Create: `src/testmod/java/com/jedts/theeconomist/gametest/CitizenFarmingTradeGameTests.java`
- Create: `src/testmod/resources/fabric.mod.json`
- Modify: `build.gradle`
- Modify: `README.md`

**Interfaces:**
- Consumes: Tasks 1–7 complete.
- Produces: automated world-level regression coverage and concise play instructions.

- [ ] **Step 1: Add Fabric GameTests for the irreversible world transitions.** Register a test entry point in the testmod resources used by Loom. Tests spawn a Citizen with grass/dirt/mature wheat, then assert a finite hoe, claim ownership, actual planted/harvested drops, save/reload survival, player harvesting on a Citizen claim, explicit shift-hoe challenge with no ownership transfer, death drops/release, and one successful Crown trade. Seed randomness with a fixed source or inject a deterministic work planner so tests are repeatable.

```java
assertEquals(playerOwner, claims.ownerAt(playerBlock).orElseThrow());
assertTrue(citizen.farmInventory().count(Items.WHEAT) > 0);
assertEquals(beforeTotalCrowns, afterTotalCrowns);
```

- [ ] **Step 2: Run the new GameTest task and record its actual task name and result.** If Loom exposes `runGametest`, use:

```powershell
.\gradlew.bat runGametest
```

If the task name differs, run `.\gradlew.bat tasks --all` once, identify the GameTest task registered by the testmod configuration, and use that task. A missing task is a configuration failure to fix in this task, not a reason to skip world verification.

- [ ] **Step 3: Document the controls and limits.** Update `README.md` with new Citizen farming and trading instructions, four-way same-owner plots, physical change requirements, the 6,000-tick price cadence, deliberate shift-hoe challenge, and the stated crash-save limitation. Keep the player-to-player trade instructions intact.

```markdown
Right-click a Citizen and choose Trade to buy its harvested wheat with Crowns.
Use a hoe on unclaimed farmland to claim it. Shift-use a hoe on Citizen-owned
farmland to challenge its owner; ordinary farming remains allowed.
```

- [ ] **Step 4: Run all unit, compile, and GameTest checks plus a two-client manual pass.** Manual checks: a fresh Citizen grows and harvests wheat; player A and B claims touch but remain separate; player harvests Citizen claim without combat; shift-hoe may trigger combat but never transfers land; wheat price updates only after 6,000 ticks; buying with exact coins succeeds; missing change refuses without item movement; seed sale succeeds only after Citizen has funds.

```powershell
.\gradlew.bat clean test classes runGametest
```

- [ ] **Step 5: Commit if the repository exists; otherwise report the verified file set and tests.**

```powershell
git add build.gradle README.md src/testmod
git commit -m "test: verify Citizen farming trade in world"
```
