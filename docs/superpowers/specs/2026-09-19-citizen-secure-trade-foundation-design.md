# Citizen and Secure Trade Foundation Design

Date: 2026-09-19
Status: Proposed for consolidated user review

## Purpose

This design defines the first playable slice of The Craft Economist: a custom Citizen entity, Crown currency items, and a secure player-to-player trade flow. It establishes readable, modular foundations for the larger population and country simulation described in the README without attempting to build the whole simulation at once.

This milestone deliberately does not implement Citizen jobs, Citizen trading, household formation, childbirth, construction, farming, mining, armies, markets, taxation, government, or demographic simulation. Those systems will build on the identity, entity, currency, persistence, networking, and transaction boundaries established here.

## Success Criteria

The milestone is complete when:

- A custom, player-shaped Citizen can be created with a spawn egg or `/summon`.
- Each Citizen has a stable generated identity and a configurable display name.
- Citizens can use cached Minecraft profile skins from a configured username pool, with a deterministic Steve/Alex-style fallback.
- Citizens are mortal, passive, avoid hazards, and flee when hurt.
- Copper, Silver, and Gold Crowns exist as currency items with fixed values.
- One player can request a trade with another player, both can accept the request, and a bilateral trade screen opens.
- Both players can offer items, mark themselves ready, and complete the exchange after a three-second confirmation countdown.
- Cancellation, disconnects, deaths, restarts, and duplicate packets cannot silently delete or duplicate offered items.
- The implementation is split into focused modules and covered by automated tests where practical.

## Target Platform

- Minecraft: 26.3
- Fabric Loader: 0.19.5
- Fabric API: 0.161.0+26.3
- Fabric Loom: 1.17.21
- Gradle: 9.6
- Java toolchain and bytecode release: 25

The versions above match the existing project scaffold. Version changes are outside this design and require a separate compatibility pass.

## Module Architecture

The existing `TheEconomistModule` and `ModuleLoader` foundation remains the top-level composition mechanism. The common entry point registers server-safe modules in dependency order:

1. `CurrencyModule`
2. `CitizenModule`
3. `TradeModule`

Client-only registrations live behind a Fabric client entry point and are never loaded by a dedicated server. Modules expose only registration and initialization responsibilities; domain logic lives in focused services and state objects.

The intended package shape is:

```text
com.illegaltempo.thecrafteconomist
|-- TheEconomistMod
|-- module
|   |-- TheEconomistModule
|   |-- ModuleLoader
|   `-- ModModules
|-- currency
|   |-- CurrencyModule
|   |-- CrownItems
|   `-- CrownValues
|-- citizen
|   |-- CitizenModule
|   |-- entity
|   |-- identity
|   |-- config
|   `-- skin
|-- trade
|   |-- TradeModule
|   |-- request
|   |-- session
|   |-- persistence
|   `-- network
`-- client
    |-- TheEconomistClient
    |-- citizen
    `-- trade
```

Server code owns all authoritative state. Clients render synchronized state and submit intent such as accepting a request, moving an offered item, or toggling readiness. A client never decides that a trade completed and never supplies an authoritative item stack.

## Citizen Module

### Entity and identity

The mod registers a new entity type, `theeconomist:citizen`. It does not replace, subclass for compatibility with, or alter vanilla villagers.

Each Citizen persists a versioned identity record containing:

- A stable Citizen UUID.
- A generated given name.
- A generated family name.
- A life-stage field, initially always `ADULT`.
- The configured profile username assigned for appearance, when one is available.
- Resolved profile UUID and signed texture data, when lookup succeeds.
- The resolved wide or slim player-model type.
- An identity schema version for future data migration.

The generated identity is separate from the profile username used for the skin. A Citizen named `Amina Patel` may use a skin resolved from any username in the configured profile pool. Reloading configuration affects only Citizens created after the reload; existing Citizens retain their names and appearance data.

### Configuration

Citizen identity configuration lives at `config/theeconomist/citizens.json` and uses a deliberately small JSON schema:

```json
{
  "profileUsernames": ["ExamplePlayer"],
  "givenNames": [
    "Amina", "Diego", "Haruto", "Leila", "Mateo", "Mei",
    "Nia", "Noor", "Priya", "Sofia", "Tariq", "Yuna"
  ],
  "familyNames": [
    "Adebayo", "Dubois", "Garcia", "Haddad", "Ivanov", "Kim",
    "Nakamura", "Okafor", "Patel", "Silva", "Singh", "Wang"
  ]
}
```

The built-in defaults use a broad, culturally varied set of real-world names. Parsing applies these rules:

- Values are trimmed.
- Blank entries are removed.
- Duplicate profile usernames are removed case-insensitively.
- Duplicate names are removed while preserving their first declaration order.
- `givenNames` and `familyNames` must each contain at least one usable value.
- An empty `profileUsernames` list is valid and causes deterministic fallback skins.
- Unknown fields are rejected so spelling mistakes do not silently change behavior.
- A reload is atomic: validation completes before the active configuration is replaced.

If the startup file is absent, the mod writes or uses the built-in default configuration. If the startup file is malformed, the server reports the exact validation error and uses built-in defaults. If a runtime reload is malformed, the last valid active configuration remains in use.

Operators with permission level 3 can run `/theeconomist reload`. The command reports success or a precise failure. It does not mutate already-created Citizens.

### Skin resolution

When a Citizen is created, the server randomly chooses one configured profile username and immediately assigns a deterministic Steve/Alex-style fallback derived from the Citizen UUID. Profile resolution then runs asynchronously so entity spawning and the server tick are never blocked by a network request.

The skin service:

- Resolves the profile UUID and signed texture property using the supported Minecraft session/profile services.
- Enforces bounded timeouts and a small request rate limit.
- Caches successful results by normalized username.
- Negatively caches failed or missing profiles for a bounded period.
- Persists successful signed texture data on the Citizen so it survives restarts and does not require repeated lookups.
- Synchronizes the resolved appearance to tracking clients when it changes.
- Keeps the fallback appearance if lookup fails or times out.

A failed lookup is not retried every tick. A later Citizen using the same username may retry only after the negative-cache period expires.

### Rendering

The client renders Citizens with a player-shaped model supporting both wide and slim arms. It displays the generated Citizen name above the entity and renders ordinary held items, equipped items, and armor through standard equipment layers. Clothing-specific assets are intentionally deferred; later clothing will be implemented as armor or equipment rather than baked into Citizen skins.

Normal player-like idle, walking, head-look, hurt, and death animations are sufficient for this milestone.

### Baseline behavior

Citizens have normal health, take damage, and can die. The initial goal set includes swimming, basic hazard avoidance, fleeing an attacker after taking damage, wandering, and looking at nearby entities. Citizens do not attack.

Citizens have no job, inventory economy, housing need, family relationship, reproduction behavior, service behavior, schedule, faction, or trading interface in this milestone.

### Creation

Citizens enter the world only through:

- A Citizen spawn egg available to creative-mode operators.
- `/summon theeconomist:citizen`.

Natural spawning and population growth are deferred until their simulation rules are designed.

## Currency Module

The currency module registers three ordinary stackable items:

| Identifier | Display name | Value |
| --- | --- | ---: |
| `theeconomist:copper_crown` | Copper Crown | 1 |
| `theeconomist:silver_crown` | Silver Crown | 10 |
| `theeconomist:gold_crown` | Gold Crown | 100 |

Each item stacks to 64, has a model, texture, localized name, and tooltip showing its Crown value, and belongs to the `theeconomist:currency` item tag. The items are visible in the mod's creative item group and can be obtained with `/give`.

Values are defined once in common code and used for display and trade summaries. The server calculates totals from registered item identities; clients cannot declare arbitrary values or counterfeit currency by changing names or NBT/data components.

There are no crafting recipes, denomination-conversion recipes, survival sources, banks, mints, fees, taxes, or price rules yet. The trade interface displays the total Crown value present in each public offer, but Crowns remain ordinary offered item stacks.

## Trade Module

### Initiating a request

A player initiates a trade by shift-right-clicking another player with an empty main hand. The server accepts the request only if both players are:

- Alive and connected.
- In the same dimension.
- Within 16 blocks of one another.
- Not already participating in another request or trade session.

Each player may participate in at most one active request or session. Repeating the interaction for the same active pair reuses the existing request rather than creating duplicates.

An active request contains a unique request UUID, both participant UUIDs, an expiration tick, and an independent acceptance flag for each participant. Both flags begin unchecked. The request expires after 15 seconds.

### Request HUD and controls

Both players see the compact, bottom-right request HUD approved as layout A. It shows two compact participant rows, each with the player's head, name, and an acceptance tick. The local player's row and the other player's row remain visually distinct.

The default controls are:

- `R`: accept the request.
- `X`: decline the request.

Both bindings are rebindable and appear in a dedicated The Craft Economist key-binding category. The UI cannot rely on color alone: checked and unchecked icons have distinct shapes or marks.

When either player declines, or when the server cancels the request, the HUD disappears on both clients with a short 250-millisecond fade-out. Damage alone does not cancel a request. Death, disconnect, dimension change, exceeding 16 blocks, or reaching the 15-second timeout cancels it immediately.

The trade screen opens only after the server has recorded acceptance from both players.

### Trade screen

The bilateral trade screen contains:

- The two player heads and names.
- A 6-by-3 public offer grid for each player, for 18 offered stacks per participant.
- A readiness indicator for each player.
- The calculated Crown total for each offer.
- The local player's normal 27-slot inventory and 9-slot hotbar.
- A local Ready/Not Ready control.
- A Cancel control.
- A visible three-second countdown overlay when both players are ready.

Each player can edit only their own offer using their own inventory. The other player's private inventory is never synchronized or displayed. The remote offer grid is read-only. The server validates normal clicks, shift-clicks, number-key swaps, dragging, double-click collection, creative actions, and any other supported slot interaction so none can access a slot outside the declared screen layout.

### Session state machine

The authoritative session state machine is:

```text
OPEN -> COUNTDOWN -> COMMITTING -> COMPLETED
  |         |             |
  `-------> CANCELLING <---'
                 |
                 `-> CANCELLED
```

In `OPEN`, either player may edit their own offer and toggle readiness. When both are ready, the server enters `COUNTDOWN` for 60 ticks. Any accepted offer edit during the countdown returns the session to `OPEN` and clears both readiness flags. The client displays server-derived remaining time; it does not run an authoritative local timer.

At the end of the countdown, the server revalidates both players, the session state, distance, dimension, and all escrow contents before entering `COMMITTING`. Completion is idempotent: a unique transaction/session ID and terminal state prevent the same transfer from running twice.

### Escrow and item ownership

Offered items move into persistent server-owned escrow when the server accepts an offer-grid operation. Each escrow stack records the unique session ID, offering player's UUID, slot identity, and serialized stack data. Network packets describe player intent; they never provide the authoritative stack to deposit or withdraw.

On successful completion, each participant receives the other participant's escrowed stacks. The server first fills the receiving player's inventory. Any remainder becomes owned item entities dropped at that receiving player's feet. Overflow does not cancel an otherwise valid trade.

On cancellation, every escrow stack returns to its original owner. The server first fills that owner's inventory and drops unavoidable overflow at the owner's feet when the player is safely present. If immediate return is unsafe because the owner disconnected, the escrow remains persisted and is recovered at the owner's next login.

The recovery policy always favors returning an item to its recorded original owner over guessing that a transfer completed.

### Cancellation rules

An active request or trade session is cancelled when:

- Either player explicitly declines or cancels.
- Either player closes the trade screen.
- Either player dies.
- Either player disconnects.
- Either player changes dimension.
- The players move more than 16 blocks apart.
- A request reaches its 15-second timeout.
- The server detects invalid or unrecoverable session state.

Taking damage without dying does not cancel a request or active trade.

Death handling cancels and returns escrow before vanilla death-inventory processing can create ambiguous ownership. Disconnect handling persists return work before the player entity is discarded.

### Restart and crash recovery

Active trade sessions and escrow records are persisted to server save data. During a controlled shutdown, the server attempts to cancel active sessions and return their escrow. On startup and player login, recovery follows explicit state rules:

- `OPEN` and `COUNTDOWN`: cancel and return each offer to its original owner.
- `CANCELLING`: resume unfinished returns.
- `COMMITTING`: use per-recipient delivery markers to resume only incomplete deliveries.
- `COMPLETED` and `CANCELLED`: perform no transfer work.

Every delivery or return operation writes its durable marker before the corresponding escrow entry can be discarded. Replaying recovery therefore cannot duplicate a fully marked delivery, while an unmarked escrow entry remains recoverable.

No error path may silently delete an escrowed item.

### Networking and security

Every trade packet is validated against:

- The authenticated sending player.
- The referenced request or session UUID.
- Membership in that request or session.
- The current authoritative state.
- Monotonic or otherwise non-replayable action sequencing where needed.
- Slot ownership and legal slot bounds.
- Current dimension and distance constraints.
- Item-count and stack-limit rules.

Stale or duplicate packets are ignored safely. Repeated invalid packets are rate-limited in logs. A malformed packet cannot move items belonging to another player, force readiness for another player, shorten the countdown, or complete a session.

### Audit logging

Every successful trade writes a structured server log entry containing:

- Transaction UUID.
- Both player UUIDs and current names.
- Completion timestamp.
- Item identifiers and counts delivered in each direction.
- Crown totals offered in each direction.
- Counts of stacks or items dropped as overflow.

Cancellation and recovery failures log the session UUID, state, affected owner, and exact cause without printing signed skin properties or other unnecessary sensitive data.

## Client Responsibilities

Client-only code provides:

- Citizen model and skin rendering.
- Currency item assets and localized text.
- Rebindable accept and decline keys.
- The request HUD and fade animation.
- The bilateral trade screen.
- Readiness and server-derived countdown presentation.

The compact request HUD must remain readable at supported GUI scales and common aspect ratios. Player heads, labels, and tick states should not overlap the hotbar, chat, or status-effect area under normal layouts. The trade screen should clearly distinguish local editable slots from remote read-only slots.

## Error Handling

- A malformed Citizen configuration reports its exact field and keeps the last valid runtime configuration.
- An empty profile pool is valid and uses fallback skins.
- Profile lookup failures never block spawning or the server tick.
- Missing client skin data renders the deterministic fallback.
- Invalid trade requests are rejected with a short player-facing reason when appropriate.
- Invalid screen actions leave authoritative inventories unchanged.
- A synchronization error causes a server resync or safe cancellation, not a speculative client correction.
- Persistence failures are logged prominently and retain recoverable escrow data whenever storage permits.
- Recovery never assumes a transfer completed without its durable delivery marker.

## Testing Strategy

### Unit tests

Automated unit tests cover:

- Citizen config parsing, trimming, duplicate removal, unknown fields, defaults, and atomic reload behavior.
- Deterministic name and fallback-model selection using seeded randomness or fixed UUIDs.
- Crown denomination values and total calculation.
- Trade request and session state transitions.
- Readiness reset after every supported offer edit.
- Countdown timing and revalidation.
- Packet/session membership and slot-bound validation.
- Idempotent commit, cancellation, return, and recovery operations.

### Game and integration tests

Fabric game tests or focused integration tests cover:

- Citizen creation by spawn egg and `/summon`.
- Persisting Citizen identity and resolved appearance.
- Passive behavior, damage, fleeing, and death.
- Request expiry, decline, range, dimension, death, and disconnect cancellation.
- Opening the trade screen only after two acceptances.
- Escrow deposits and withdrawals.
- Successful exchange with normal inventory capacity.
- Overflow items appearing at the receiving player's feet.
- Cancellation returning items to original owners.
- Restart recovery from `OPEN`, `COUNTDOWN`, `CANCELLING`, and partially delivered `COMMITTING` states.
- Duplicate and stale packets having no duplicate effect.

### Client checks

Manual client checks verify:

- Wide and slim Citizen skins, deterministic fallback skins, and armor rendering.
- Nameplates and normal entity animations.
- Request HUD layout, both acceptance ticks, fade-out, and GUI-scale behavior.
- `R` and `X` defaults plus key rebinding.
- Public offer visibility and remote-slot read-only behavior.
- Three-second countdown display and reset on edit.
- Dedicated-server startup without loading client-only classes.

## Documentation Updates

Implementation will update the README with:

- The exact Citizen configuration path, schema, and reload command.
- Citizen spawn egg and `/summon` examples.
- Crown item identifiers and values.
- Player trade initiation, controls, timeout, range, readiness, cancellation, and overflow rules.
- A clear milestone status separating implemented behavior from the long-term population simulation plan.

## Out of Scope

This milestone does not include:

- Citizen-to-player or Citizen-to-Citizen trading.
- Citizen inventories, wages, prices, scarcity, production, or consumption.
- Building houses, farming, mining, military service, or other service contracts.
- Babies, aging, marriage, households, inheritance, migration, disease, education, or social classes.
- Settlements, countries, laws, taxation, government, war, diplomacy, or elections.
- Natural Citizen spawning or autonomous population growth.
- Clothing assets beyond standard armor/equipment support.
- Mailboxes or deferred delivery to a mailbox.
- Survival recipes or sources for Crowns.

## Delivery Stages

Implementation planning should break this design into the following dependency-ordered stages:

1. Shared registration, serialization, saved-data, and networking infrastructure.
2. Crown items, values, assets, tags, localization, and tests.
3. Citizen identity configuration, entity registration, passive AI, persistence, fallback rendering, and tests.
4. Asynchronous profile resolution, caching, synchronization, and custom skin rendering.
5. Trade-request state, interaction hook, packets, key bindings, request HUD, cancellation, and tests.
6. Trade screen, persistent escrow, readiness, countdown, idempotent commit, overflow delivery, and tests.
7. Restart recovery, audit logging, end-to-end verification, README updates, and dedicated-server checks.

Each stage must leave the project buildable and keep client-only code isolated from the dedicated server.
