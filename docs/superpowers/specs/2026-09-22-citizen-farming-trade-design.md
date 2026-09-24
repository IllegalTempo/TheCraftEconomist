# Citizen Farming and Crown Trade Design

Date: 2026-09-22
Status: Design sections approved by the project owner; written spec awaiting review

## Purpose and scope

Players can buy wheat from Citizens with physical Crowns. The wheat must come from a Citizen's own farming and gathering. Citizens autonomously claim unclaimed farmland, hoe nearby land, gather wheat seeds, plant, and harvest. Players can claim farmland too. Claims record ownership without preventing players from working or harvesting a claimed field. A deliberate challenge can provoke a short fight, but combat never transfers land.

This is one playable vertical slice. It does not add settlements, general markets, all crops, automated construction, taxes, land sales, or farming in unloaded chunks.

## Success criteria

- A newly spawned Citizen starts with a basic hoe and can obtain wheat seeds from nearby grass without creating seeds from nothing.
- The Citizen claims unclaimed farmland, hoes adjacent tillable blocks, plants seeds, harvests mature wheat using normal drops, saves its inventory, and replants using retained seeds.
- A player can claim unclaimed farmland by using a hoe on it. Different owners' fields remain separate plots even when their farmland blocks touch.
- Players can hoe, plant, and harvest claimed land. Shift-using a hoe on a Citizen's claimed farmland deliberately challenges the claim and may trigger a fight.
- A Citizen can sell its own harvested wheat for physical Crown items and use earned Crowns to buy wheat seeds or replacement wooden hoes from players.
- Wheat prices change with the Citizen's stock at most once per 6,000 game ticks. Trading between updates uses the saved price snapshot.
- Server checks prevent stale, repeated, out-of-range, unfunded, or inventory-overflow trade requests from duplicating or deleting items during normal operation.
- Saves and reloads preserve claims, Citizen inventory, and price snapshots. Citizen death drops its inventory and releases its claims.

## Existing foundation

The project already has a custom Citizen entity with identity, stats including anger, a job label, and an information screen. It has Copper, Silver, and Gold Crown items valued at 1, 10, and 100. The existing player-to-player trade flow is separate; Citizen transactions are immediate server-side exchanges and do not reuse its request or countdown state.

## Plot ownership

A plot is a maximal four-way-connected set of farmland blocks in one dimension **with the same owner**. Touching corners do not connect blocks. Adjacent farmland belonging to different owners remains in separate plots. An owner is a player UUID, a Citizen UUID, or unclaimed. The per-block ownership map is authoritative; plot grouping is derived from it. This avoids silently changing ownership when two fields meet. Same-owner components merge when connected and split when farmland between them disappears.

Using a hoe on unclaimed farmland claims the connected unclaimed component for the user. Hoeing dirt or grass creates a farmland block owned by the actor. A Citizen can extend only its own plot or claim unclaimed farmland. A player may work another owner's farmland, including hoeing nearby blocks, without automatically taking that owner's blocks. Neither ordinary farm actions nor combat transfer an existing claim. Trampling or removal deletes only the ownership record for farmland that actually ceases to be farmland. A dead Citizen's remaining claims become unclaimed, not the challenger's property. Player claims persist through player death and logout.

Claim data lives in dimension-scoped saved data indexed by block position and owner UUID. Event handlers update it only on the server. Reconciliation removes records for blocks that are no longer farmland. A bounded incremental scan handles large changes without forcing chunks to load; unloaded records remain saved until their chunk is loaded again. Plot lookups and scans use bounded per-tick work.

## Farmer behavior and inventory

The first crop is wheat. A Citizen starts with one wooden hoe as basic equipment. It searches a limited radius in loaded chunks for unclaimed farmland or safe, accessible tillable ground. It prefers an existing owned plot, then an unclaimed field, then new ground. It never modifies protected containers or other special blocks, does not force chunks to load, and pauses when it cannot reach a work target.

The farmer collects actual wheat seeds from grass block drops, plants one seed per empty owned farmland block, waits for natural growth, and collects ordinary drops from mature wheat. It retains enough seeds to replant its field before offering seeds to a player or consuming more grass. Hoe use consumes durability. Harvested wheat, seeds, hoes, and Crowns occupy a persistent Citizen inventory with finite capacity. Full inventory, missing seeds, or no usable hoe pauses the relevant task and surfaces a reason in the Citizen interface. The Citizen does not harvest or replant other owners' plots automatically. It reserves four wheat from sales as an initial personal stock reserve; this milestone does not add food consumption.

Only loaded, living Citizens run the farming loop. The work scheduler uses a bounded server-tick budget and performs block changes through server-authoritative game mechanics. On death, the Citizen drops its inventory once and releases its claims.

## Prices and land valuation

Each Citizen stores a price snapshot and the game tick at which it was calculated. At most once every 6,000 game ticks, including once after loading an overdue Citizen, the server recalculates wheat's unit sale price from its sellable wheat stock. The initial formula is `clamp(4 + ceil((16 - sellableWheat) / 8), 2, 6)` Crowns per wheat. Seed purchase price is 1 Crown each and a wooden hoe purchase price is 8 Crowns; these are part of the same snapshot for consistent quotes. Prices are integer Crowns. A sale cannot occur with zero sellable wheat regardless of its stored quote. There is no background price update for an unloaded Citizen.

Land price is a **decision metric**, not a charge or land-sale offer. The server samples loaded blocks within 16 horizontal blocks of the plot center. `unclaimedCount` is the number of unclaimed dirt, grass, or farmland blocks in that sample, capped at 64. `scarcity = 1 - unclaimedCount / 64`. The plot's expected one-harvest value is `min(farmlandBlockCount, 64) * currentWheatPrice` Crowns, using the deciding Citizen's price snapshot. `threat = clamp((opponentHealth + 2 * opponentArmor) / max(1, citizenHealth + 2 * citizenArmor), 0, 2)`. `netValue = clamp((expectedValue - 8 * threat) / 128, 0, 1)`. These are bounded decision inputs, not item or Crown transfers.

## Challenges and combat

Shift-using a hoe on a Citizen-owned farmland block starts an explicit player challenge. Ordinary entry, hoeing, planting, and harvesting never by themselves provoke an attack. A Citizen may challenge a Citizen-owned neighboring plot when it has failed to find workable unclaimed land for 6,000 ticks. A Citizen never initiates a challenge against a player-owned plot, so it cannot attack a player over land without that player initiating a dispute. A challenge never changes ownership. The owner Citizen makes one server-side random fight decision for that challenge. The three monotonic inputs are its anger stat, expected net value of keeping access to the plot, and scarcity of nearby unclaimed tillable land. Expected net value subtracts an injury-risk estimate based on the Citizen's and opponent's current health and equipment. Higher anger, value, or scarcity cannot lower the fight chance. A per-pair, per-plot cooldown prevents rerolling the same challenge repeatedly.

The defender's fight probability is `clamp(0.05 + 0.45 * anger / 100 + 0.25 * netValue + 0.20 * scarcity, 0.05, 0.95)`. It is rolled once per challenge. The same challenger cannot trigger another roll for that plot for 6,000 ticks. If the defender fights, it targets the challenger for at most ten seconds and stops earlier when the challenger leaves a 16-block range, dies, changes dimension, or the defender falls below one-quarter health. It does not pursue across unloaded chunks or continue hostility after the incident. Creative and spectator players cannot be combat targets. A fight can harm a survival player, but it only drives a challenger away; victory, defeat, and Citizen death never award the plot to the challenger. Citizen death releases its claim as described above.

## Citizen trade flow

The existing Citizen information screen gains a Trade action. The server sends a bounded view of the Citizen's sellable wheat, saved price snapshot, desired seed/hoe inputs, available Crown funds, and reasons a specific trade is unavailable. A player can buy a chosen quantity of wheat or sell seeds/a wooden hoe to the Citizen. The Citizen's purchases are limited to inventory capacity and physical Crowns it has earned. It starts with no Crowns.

Coins remain physical items in the player and Citizen inventories. A trade may use Copper, Silver, and Gold Crowns. The server finds payment and change from the two actual inventories; if the recipient cannot make required change, the trade is unavailable. No denomination is minted, converted, or discarded to make change. The screen explains insufficient goods, insufficient funds, insufficient inventory space, and unavailable change.

The client submits only an action, quantity, Citizen identity, price-snapshot version, and one-time request ID. The server serializes execution on its main thread and rechecks the player is alive, within six blocks, in the same dimension, and viewing that Citizen; the Citizen is alive; the price snapshot and requested goods are current; all item and coin stacks still match; and both inventories can receive the result. It rejects duplicate IDs. Only after every check passes does it remove and insert the selected goods and exact coins in one server tick, then send a fresh view. A stale quote is rejected and refreshed rather than executed at a surprising price. Closing the screen has no item effect because no goods are reserved before confirmation.

Inventory and trade state use normal Minecraft saves. An abrupt process crash between separate entity and player saves is outside this first slice's durability guarantee; the server does not claim crash-safe escrow. The UI should not imply otherwise. This limitation is separate from in-session validation and duplicate-request protection.

## Interfaces and error handling

The plot registry exposes ownership and topology queries plus block-change updates. The farmer service reads those queries and acts only through validated world changes and its inventory. The pricing service accepts stock and game time and returns an immutable quote. The conflict service accepts owner, challenger, plot, stats, and world sample, then produces one decision. The trade service accepts player intent and current server state and returns a success or explanatory refusal. Client packets contain no authoritative inventory contents or price calculations.

When a work action fails because terrain changed, another Citizen acted first, a chunk unloaded, or inventory space vanished, the service abandons that action and chooses another target on a later tick. When a trade precondition changes, nothing moves and the server sends the updated quote. Malformed or out-of-range packets are ignored or refused without changing state.

## Verification

Unit tests cover same-owner connection and split, different-owner adjacency, claim release, price cadence and stock response, fight-chance monotonicity and cooldown, coin selection/change, duplicate trade requests, and all-or-nothing inventory checks. Game tests cover spawning and saving a farmer, hoeing/seed gathering/planting/harvesting, player work on claimed farmland, challenge disengagement, Citizen death drops, and buying wheat plus selling inputs on a dedicated server. A manual two-client check covers the Trade screen, stale quote feedback, and price update after a quarter Minecraft day.
