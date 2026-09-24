# The Economist

The Economist is a planned Minecraft Java Edition mod about building and living inside a simulated country. Instead of villagers offering fixed, isolated trades, people form households, earn wages, buy necessities, have children, choose jobs, migrate, and respond to the local economy. Players can trade with them directly or hire organized groups to build, farm, mine, and defend settlements.

The goal is not to reproduce every detail of a modern country. The goal is to create believable cause and effect: a mine creates jobs and stone, workers require food and homes, new homes attract families, families raise children, taxes fund guards, shortages raise prices, and war or disaster can disrupt the whole chain.

## Project status

This repository contains a modular Fabric foundation, Crown currency, a first citizen slice, blueprints, and a playable player-to-player trade flow. The larger population simulation remains a planned roadmap.

Planned initial platform:

- Minecraft Java Edition 26.3, the latest stable release when this design was written
- Fabric Loader 0.19.5 or newer compatible release
- Fabric API
- Java 25
- Multiplayer-safe, with the server authoritative over simulation state

Current dependency versions are defined in `gradle.properties`.

### Build the foundation

Requirements:

- JDK 25
- An internet connection for the first Gradle dependency download

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The remapped development JAR is written to `build/libs/`. The build runs the JUnit and server GameTest suites. Crown items are currently obtainable through Creative mode or `/give`; they have no crafting or survival source yet.

### Code organization

- `TheEconomistMod` is the small Fabric composition root.
- `TheEconomistModule` is the contract implemented by future feature packages.
- `ModModules` is the inventory of enabled features; dependency metadata determines initialization order and list order breaks ties.
- `ModuleLoader` validates module IDs and dependency graphs before initializing features.

Future systems should live in focused feature packages and register through a module instead of adding unrelated setup code to the Fabric entry point. See [Adding a Feature](docs/architecture/adding-a-feature.md) for the module, behavior, persistence, and test workflow.

### Adding a citizen skill

Citizen skills are code-registered in `CitizenSkillRegistry`. Add a stable namespaced
ID and a legacy save key to the registry's ordered list; `CitizenSkills`, training,
highest-skill queries, and both persistence paths discover the new skill automatically.
Use the generic `value`, `values`, and `train` APIs in new code. Keep IDs stable after
release so existing citizen saves continue to load; missing values default to zero.

Citizen stats follow the same pattern in `CitizenStatRegistry`. Each stat defines a
stable ID, legacy save key, and default value; generic stat access and persistence
discover newly registered stats automatically.

### Adding a Citizen behavior

Citizen AI uses `CitizenBehaviorController` rather than Minecraft goals. Implement
`CitizenBehavior` with a stable `id`, live score evaluation, eligibility, lifecycle cleanup, tick,
and status text, then register its factory in `CitizenModule.createBehaviorRegistry()`. All current
behaviors use the shared score across urgency, benefit, capability, opportunity, cost, and risk.
The controller runs one behavior at a time, keeps the current action until a challenger leads by
five points, and lets emergency overrides take precedence. Each behavior that navigates or interacts with blocks must release
its path, target, reservation, and other runtime state from `stop`.

Add unit coverage for selection or any pure decision code and a GameTest for visible
world behavior. The default tie order is emergency escape, panic, monster avoidance,
combat, sleep, confusion, seed finding, composter work, farming, contracts, returning home,
looking at creatures, wandering, and idle. Household Citizens search the generated house for an available bed at
night, reserve it for that night, walk to it, and use the vanilla sleeping pose.
Citizens without a generated household, or whose household record is missing, enter
`confused` until daytime. Right-click information shows the active behavior and its
current status, plus a generic **Decision** tab with the server's live candidate scores,
factors, explanations, and selection state used by the controller.

Decision scoring is tunable in `config/theeconomist/citizens.json`, under
`decisionScoring`. The config is created or upgraded with defaults automatically.
The `profileUsernames` list supplies the public Minecraft profiles used for citizen
skins; replace these names with the accounts you want the generated citizens to use.
Edit the shared `costPenalty`, `riskPenalty`, and `switchingMargin` values or an
action's named values under `decisionScoring.actions`, then run `/citizen reload`
as an operator to apply them. For example, `actions.farmer.urgencyAmbition`
changes how strongly ambition affects farming urgency, while `actions.farmer.workRadius`
changes the farming work radius. Unlisted scoring settings retain their built-in
defaults; invalid or unknown settings are rejected and the last valid configuration
stays active.
The ambient `look_at_creature` action uses the same scoring formula with a deliberately
low default reward, and can target players, Citizens, and other living mobs. Existing
`look_at_player` range values are migrated to `look_at_creature` when the config loads;
sociability-only overrides are dropped.

## Design principles

1. **People, not vending machines.** Every adult has a household, job, needs, skills, money, and relationships that influence decisions.
2. **Visible causes.** Players should understand why a price changed, a worker resigned, or a family moved.
3. **Useful gameplay first.** Economic realism must create interesting choices rather than repetitive administration.
4. **No free resources.** NPC labor consumes time, tools, food, materials, and wages. Services transform or transport resources instead of creating them.
5. **Local simulation.** Towns have their own inventories, wages, prices, laws, and public budgets.
6. **Scalable simulation.** Nearby citizens use entity AI; distant citizens use lightweight statistical simulation.
7. **Player agency without total control.** Citizens can refuse unfair contracts, change careers, protest, migrate, or defend themselves.

## Player Trade system

To request a trade, sneak and right-click another player with an empty main hand while both players are within 16 blocks in the same dimension. A trade request screen opens for both players. Both must accept within 15 seconds. Each player can then click inventory slots to pledge up to 18 entire stacks; both offers are visible to both players. When both mark ready, a three-second server countdown starts. The server rechecks the items and player positions before exchanging them. Changing an offer clears both ready flags. Overflow items drop at the receiving player's feet.

Offers remain in their owners' inventories until the countdown finishes, so moving or changing a pledged stack invalidates the offer. Escape, decline, death, disconnect, dimension change, or moving beyond 16 blocks cancels the trade. Crash-safe escrow and restart recovery remain future work; do not use this first trade flow for high-value exchanges on a server where crash recovery is required.

The current request uses on-screen Accept and Decline buttons. The originally planned rebindable `R` and `X` shortcuts, request HUD, durable escrow, and citizen value-based trading remain later refinements.

## Citizen farming and Crown trade

Citizens start with a wooden hoe and no seeds. New Overworld terrain can generate modest oak-and-stone houses on suitable dry, reasonably level land, with a door and four beds. Each house receives two to four Citizens with a shared saved surname and distinct given names when possible. Households populate once; deaths do not refill resident slots, and normal saves and reloads do not add residents. Existing explored chunks are not changed. Natural residents save the house entrance as their home and farm within 16 blocks of it. Spawn eggs and `/summon` create independent Citizens who choose their own names and home spots, preferring dry sites near source water. Existing Citizens keep their saved names and homes. Citizens no longer receive, place, or manage home signs; old placed signs and signs already in inventories remain ordinary blocks and items. Farming leaves the home tile unhoed and Citizens return home when they wander too far. They gather real wheat seed drops from nearby short grass and ferns before hoeing new dirt or grass, claim unowned farmland, plant wheat, and harvest mature crops into a finite saved inventory. When hoeing new ground, Citizens prefer land within four blocks of natural source water, then land beside their own field, then other workable ground. Seed drops remain subject to Minecraft's normal chance, so a Citizen needs seed-bearing plants within its home work area. Before every farming interaction, the Citizen faces the target block and then uses the vanilla arm swing, block destruction effects, planting sound, or hoe sound. They keep four wheat and offer surplus to players. Right-click a Citizen for its information panel. **Overview** shows its attributes and job; **Work** shows its saved home coordinates, farming status, land, full 27-slot inventory, Crown balance, and prices; **Decision** shows the live server evaluation snapshot used by the action controller. Scroll within each page for more details. The **Trade** button opens Crown trading. The Citizen sells wheat and buys wheat seeds or a wooden hoe. Transactions move real Copper, Silver, and Gold Crowns between inventories; if exact payment and physical change cannot be made, the trade is refused without moving items. Wheat prices update once every 6,000 game ticks (one quarter of a Minecraft day).

Use a hoe on unclaimed farmland to claim it. Farmland that touches another owner's field stays separately owned; each plot connects only four-way through farmland with the same owner. Claims record ownership while players may still hoe, plant, and harvest normally. Shift-use a hoe on Citizen-owned farmland to deliberately challenge its owner. A Citizen may fight according to anger, estimated land value, and nearby scarcity. Combat is timed and never transfers ownership.

The Citizen inventory and claims are saved. A process crash between vanilla player and entity saves can interrupt a trade save; crash-atomic recovery is not implemented in this milestone.

## Citizens

The main NPC is a **Citizen**. Citizens use a villager-like visual language, but their behavior is driven by the population simulation.


Each citizen has persistent data:

- Name and unique identity
- Age stage: baby, child, adolescent, adult, or elder
- intelligence: affect decision accuracy
- Household and close family relationships
- anger: build up from wealth disperity, crime experienced.
- Home settlement and current residence
- Health, hunger, energy, safety, and morale
- Education and job skills
- Occupation, employer, wage, and work schedule
- Personal wallet and household savings
- Traits such as ambition, thrift, bravery, sociability, and loyalty
- Reputation toward players, employers, and settlements
- Current goals and recent memories
- mental status: result in crime or inversed decision
Typical daily routine:

| Time | Behavior |
| --- | --- |
| Dawn | Wake, eat, care for children, travel to work |
| Day | Perform paid work or education |
| Late day | Shop, deliver goods, socialize, or do household tasks |
| Night | Return home, eat, rest, and seek safety |

Hunger, danger, illness, childcare, weather, and urgent contracts can override the routine.

## Needs and quality of life

Citizens evaluate their lives through:

- **Food:** quantity, variety, and recent nutrition
- **Housing:** safe bed, shelter, space, light, and basic furniture
- **Safety:** protection from mobs, raids, crime, and war
- **Health:** injury, illness, age, and access to care
- **Belonging:** family contact, community, and social spaces
- **Prosperity:** disposable income, savings, and desired goods
- **Purpose:** stable work, education, status, and contribution

These values form household **quality of life**. It influences morale, work effort, birth decisions, migration, crime, and political support. The UI shows the strongest causes, not just an unexplained number.

## Households, relationships, and children

A household is the basic economic unit. Members share housing, food storage, childcare, and some money. It may contain one citizen, a couple, parents and children, an extended family, or unrelated housemates.

Adults may form partnerships when they know and trust each other, have compatible personalities, have time to socialize, can access suitable housing, and are not close relatives.

Partners may choose to have a baby when the household has adequate housing, food security, safety, health, and confidence in its future. Births are not a random timer. Poor conditions lower or pause birth rates; stable conditions increase them gradually.

Pregnancy and birth are family-friendly and non-graphic. Babies require food, shelter, and adult care. Children attend school or learn at home, adolescents may apprentice, adults enter the labor market, and elders can teach, provide childcare, or lead.

```text
population next year = current population + births + immigration - deaths - emigration
```

Time is compressed. The proposed default is one Minecraft year per 120 in-game days, configurable by the server. Ageing can be disabled without disabling households.

Safeguards:

- Close relatives cannot become partners.
- Planned births require valid housing capacity.
- Population caps are configurable per settlement and server.
- Children do not fight, mine, or perform dangerous paid work.
- Orphaned children are adopted by relatives or another suitable household when possible.

## Money, property, and trade

The currency is the **Crown**, currently available as physical item stacks:

- `theeconomist:copper_crown` - 1 Crown
- `theeconomist:silver_crown` - 10 Crowns
- `theeconomist:gold_crown` - 100 Crowns

Use `/give @s theeconomist:copper_crown`, `/give @s theeconomist:silver_crown`, or `/give @s theeconomist:gold_crown` to obtain them during development. Crafting, denomination conversion, banks, minting, fees, taxes, and survival sources are outside the current milestone.

Coins can be carried, stored, stolen, lost, or paid directly. Banks later enable large payments without moving stacks.

Money enters through configured sources such as founding grants, government minting, outside trade, and rewards. It leaves through fees, imports, and optional administrative sinks. Large transactions record their source and destination to diagnose inflation.

Property can belong to a citizen, household, business, government, or player. Early releases use simple claimed worksites and owned containers; detailed plots and leases come later.

Each settlement estimates market prices from:

- Current local stock
- Recent production and consumption
- Household and employer demand
- Transport distance and danger
- Quality or durability
- Taxes and subsidies
- Configurable price limits

```text
target price = base value × scarcity × local demand × transport cost × policy modifier
```

Prices move gradually. Citizens remember recent prices and refuse exploitative offers unless desperate. A trade screen shows quantity, unit and total price, local price range, stock or demand, taxes, and the reason an offer is accepted or rejected.

Households buy necessities first. Wealthier families may purchase decoration, books, better food, and larger homes.

## Employment and businesses

A job defines a role, workplace, hours, wage, tool and material responsibilities, work area, safety expectations, duration, and cancellation rules. Workers compare offers using wage, danger, travel time, skill fit, employer reputation, and household needs. They may resign when unpaid, repeatedly endangered, or offered substantially better work.

Businesses combine a workplace, inventory, cash account, employees, and production rules. Farms, mines, builders' yards, workshops, shops, carriers, and banks can make a profit, run short of cash, reduce hiring, or fail.

## Service contracts

Players buy services through a **Contract Desk** or qualified foreman. A contract has a quote, deposit, work boundary, material list, deadline, safety rules, and completion conditions. Large jobs become restart-safe tasks.

Workers reserve real items before starting. If supplies run out or the route becomes unsafe, the contract pauses and reports exactly what is missing.

### Building

The player positions an approved blueprint preview. Builders then:

1. Survey the site and reject protected or impossible placements.
2. Produce a bill of materials and labor estimate.
3. Collect blocks from designated containers.
4. Clear only blocks authorized by the contract.
5. Build foundations, structure, roof, interior, and lighting in safe stages.
6. Pause when materials are missing.
7. Validate the completed building.

Initial blueprints: small home, family home, farm shed, warehouse, market stall, barracks, and town hall. Blueprints are data-driven for modpack customization.

Builders never alter claimed land, containers, redstone, portals, or rare blocks without permission. Cancelled work remains in the world and payment is settled by completed progress.

### Farming

A farm contract specifies its field, crops, replanting rules, tools, food reserve, and destination inventory. Farmers prepare soil, maintain irrigation, plant, harvest, replant, compost surplus, tend configured livestock, and move produce to storage.

Household food reserves are protected before surplus is sold. Repeated crop failure can cause high prices, imports, rationing, migration, and eventually famine.

### Mining

A mining contract defines an approved volume or planned shaft, target resources, depth, storage point, and acceptable hazards. Miners use real tools; build stairs, supports, lights, and paths; avoid lava and protected structures; and return extracted blocks to storage.

Miners do not detect hidden ores. Discovery comes from exposed blocks and legitimate excavation, preserving survival gameplay.

### Army and security

Security begins with guards and can grow into a paid army. It consumes wages, equipment, food, housing, training, and leadership.

Roles include guard, archer, scout, soldier, captain, and quartermaster. Governments define patrols, posts, rally points, and defensive zones. Forces can protect settlements, escort caravans, and respond to declared hostilities, but do not attack neutral citizens or players automatically.

Morale depends on pay, supplies, leadership, casualties, and whether the conflict seems legitimate. Unpaid or starving troops may desert. Initial releases focus on mobs and raids; diplomacy and interstate war come later.

## Settlements and countries

A **settlement** forms around a town center, occupied homes, a shared market, and enough citizens. Its state contains:

- Population and household registry
- Housing capacity and homelessness
- Jobs and unemployment
- Stocks, production, consumption, and prices
- Treasury, taxes, wages, and expenses
- Laws and policies
- Public buildings and infrastructure
- Security, health, education, and quality of life

A **country** is a voluntary federation of settlements under a shared government. It has a name, banner, capital, treasury, citizenship rules, diplomacy, and common policies. Membership is reversible through political processes rather than being permanently owned by the founder.

## Government and public finance

Early settlements use a town council. Governance is server-configurable: player-appointed mayor, elected citizen mayor, council vote, or a permanent ruler for role-play servers.

Revenue may come from sales tax, income tax, property fees, tariffs, and contributions. Taxes are visible before transactions. The treasury pays for guards, roads, schools, clinics, construction, emergency food, and government wages. A public ledger shows all income and spending.

Policies create trade-offs:

- Food subsidy improves access but costs treasury funds.
- Minimum wage raises poor households' income but may reduce hiring.
- Child allowance supports families but increases spending.
- Tariffs protect producers but raise consumer prices.
- Conscription expands defense but lowers morale and civilian labor.
- Open migration fills jobs but increases housing demand.

## Education, health, death, and migration

Citizens improve building, farming, mining, combat, trade, logistics, medicine, teaching, and leadership through practice and mentoring. Schools improve general learning; apprenticeships train a profession. Tools, education, family wealth, and local demand all affect social mobility.

Health stays lightweight. Ordinary hazards cause injuries; later versions may add configurable illness. Food, rest, safe housing, and medical services aid recovery. Death affects relatives, employers, inheritance, and morale. A forgiving server mode can replace non-combat death with long recovery.

Citizens compare settlements by safety, wages, housing, food prices, family ties, laws, and travel danger. Migration is physical nearby and an abstract timed journey at long distance. Families try to travel together.

Potential emergencies include food or housing shortages, raids, mine accidents, fire, epidemics, and treasury insolvency. Citizens prioritize survival while governments can enact temporary policies.

## Crime and justice

Crime is a later feature and must not equate poverty with criminality. Risk depends on desperation, inequality, security, trust, opportunity, and traits. Possible crimes include theft, smuggling, vandalism, and desertion.

Justice policies support warnings, fines, restitution, temporary detention, and exile—never graphic punishment. Guards prefer arrest and de-escalation when dealing with citizens.
## Blueprint system

Blueprints have three explicit states:

- **Empty Blueprint:** contains no captured structure and can select two world corners.
- **Designed Blueprint:** contains the server-captured relative blocks and can enter placement preview.
- **Planned Blueprint:** contains a designed structure positioned at an exact world origin, rotation, mirror setting, and dimension.

### Capturing a design

Hold an Empty Blueprint and right-click two blocks to select opposite corners of an inclusive cuboid. The clicks can be in either order; clicking the same block twice captures a one-block design. A blue marker and HUD hint show the first corner. Right-clicking a chest selects it instead of opening it. Press Escape, switch items, die, disconnect, or change dimension to cancel the selection.

The server reads every non-air block inside the selected region. The design stores exact block states and available block-entity data, including chest inventory items and their components, sign text, and other persistent data. Air and entities are not captured. The server requires both corners to be within interaction reach and the whole region to be loaded, in bounds, and accessible; it rejects invalid or oversized selections without changing the item or world. Current limits are 64 blocks per axis, 4,096 selected positions, 32 KiB per block entity, and 256 KiB for the encoded design.

A successful capture turns the Empty Blueprint into a Designed Blueprint. Capturing records desired contents but never creates blocks or items. Future construction must supply real materials and respect server permissions.

### Placement preview

Right-clicking a Designed Blueprint starts placement preview:

- The HUD displays "R to rotate" above the action bar.
- The saved structure follows the player’s crosshair.
- The preview uses the original saved block models and block states.
- Preview rendering is visual-only and never replaces real world blocks.
- Preview blocks update as the player aims at different positions.
- Pressing R rotates the structure by 90 degrees.
- Right-click confirms the location and changes the blueprint to Planned.
- Holding a Planned Blueprint shows its saved blocks at the planned location in the matching dimension.

Preview blocks are rendered with the original model using a blue tint and 50% opacity. Previous preview positions are discarded as the preview moves, so they do not accumulate.

### Stored blueprint data

Each blueprint can store:

- Lifecycle state
- Structure width, height, and depth
- Relative block positions
- Block IDs
- Block-state properties such as facing, slab type, and orientation
- Block-entity data such as container contents and sign text
- Planned origin
- Rotation
- Mirror flags
- Dimension

Selection markers and placement previews exist only on the local client and never replace world blocks. The client sends only corner intent; the server captures the structure itself. Confirming placement sends a bounded proposal that the server validates against the held item, dimension, and destination. Planned blueprints retain contents as data without materializing them.

## Information and interface

- **Citizen panel:** household, job, needs, skills, goal, and recent concerns
- **Contract desk:** quotes, requirements, progress, blockers, and cancellation
- **Market board:** prices, stock trends, shortages, and wanted goods
- **Census book:** population, births, deaths, migration, jobs, and housing
- **Public ledger:** taxes, wages, service costs, and treasury history
- **Settlement map:** homes, workplaces, claims, patrols, and contracts
- **News board:** births, elections, businesses, shortages, attacks, and projects

Interfaces must answer “why?” Example: “Bread is expensive because reserves are 38% of target.”

## Simulation architecture

The server uses three detail levels:

1. **Active:** nearby citizens navigate, animate, use workstations, carry inventories, and perform individual tasks.
2. **Background:** distant citizens in loaded settlements update in batches; production is calculated from elapsed time, skills, tools, and workplace state.
3. **Abstract:** unloaded settlements update at longer intervals without loading chunks. Returning players see materialized, logged outcomes.

Technical requirements:

- Configurable server-tick simulation budget
- No forced chunk loading for ordinary routines
- Batched pathfinding
- Stuck detection and safe recovery within valid owned areas
- Persistent transaction and contract IDs to prevent crash duplication
- Versioned saved data for upgrades
- Admin diagnostics for population, tasks, and slow settlements
- Vanilla spawn protection, adventure mode, permissions, and compatible claim APIs respected by destructive services

## Development roadmap

### Phase 0 — design prototype

- Finalize citizen, household, settlement, and contract data models
- Define time, simulation levels, saving, and server configuration
- Create interface sketches and the blueprint format

### Phase 1 — playable economy

- Citizen entity, identity, wallet, hunger, home, household, and schedule
- Crown coins and direct trading
- Settlement center, census, inventory targets, and prices
- Basic farmer employment
- Save/load and multiplayer synchronization

**Milestone:** a town grows food, pays farmers, trades crops, and explains price changes.

### Phase 2 — families and construction

- Relationships, partnerships, births, ageing, and inheritance
- Housing validation and household moves
- Contract desk and contract lifecycle
- Blueprint preview and builder teams

**Milestone:** prosperous households have children, and builders construct valid homes from supplied blocks.

### Phase 3 — production economy

- Mines, workshops, businesses, skills, and apprenticeships
- Warehouses, carriers, and inter-settlement trade
- Banking, profit, failure, and unemployment

**Milestone:** linked industries operate without generating free resources.

### Phase 4 — government and defense

- Treasury, taxes, policies, public ledger, and council
- Guards, patrols, barracks, raids, emergencies, and morale
- Elections and country formation

**Milestone:** taxes fund services and a supplied, paid defense force.

### Phase 5 — national simulation

- Diplomacy, borders, citizenship, migration rules, and treaties
- Regional trade, transport costs, crime, justice, health, and education
- Distant simulation, performance work, history, and charts

**Milestone:** multiple settlements behave as a country while retaining local economies.

## First public alpha

The alpha is intentionally smaller than the complete design:

- One citizen type with adult and child stages
- Households, homes, hunger, morale, simple births, and ageing
- Crown coins and a local supply-and-demand market
- Farming as the first complete paid service
- A limited blueprint-building service
- One settlement with census and public ledger
- Defensive guards against hostile mobs

Mining, countries, elections, advanced warfare, crime, disease, and full distant simulation follow after the economic core is stable.

### Alpha acceptance criteria

- A survival world can create a settlement without commands.
- Ten citizens survive 30 Minecraft days and retain homes, jobs, and inventories after restart.
- Farmers consume tools and time, harvest real crops, reserve food, and sell surplus.
- Builders use exactly the supplied blocks and report shortages.
- Eligible adults form a household and have a child only under valid conditions.
- Prices rise during measured shortages and recover with supply.
- Guards protect the settlement without attacking neutrals.
- Every Crown payment records payer, payee, amount, and reason.
- Simulation stays inside its tick budget at the supported population cap.
- Important behavior works on a dedicated multiplayer server.

## Non-goals

- A perfect academic economic model
- Modern firearms or graphic warfare
- Instant structure generation disguised as labor
- Fully autonomous megacities with no player involvement
- Human-level or generative NPC conversation
- Simulation of real-world ethnicities, religions, or nations
- Compatibility with every terrain, automation, and claim mod at launch

## Open design decisions

- Are Crowns crafted, government-issued, or server-configurable?
- Do Citizens replace vanilla villagers or coexist with them?
- What are the default ageing speed and population cap?
- How does a player earn initial settlement authority?
- How detailed should rent, land ownership, and business shares become?
- Which claim and map mods receive first-class integration?
- Are countries player-led, citizen-led, or server-configurable?
- How much military control is appropriate in multiplayer?

## Naming

**The Economist** is a working title. It may be confused with the publication of the same name, so a distinct release name should be selected before public distribution.

## License

No license has been selected. Until one is added, all rights are reserved by the project owner.
