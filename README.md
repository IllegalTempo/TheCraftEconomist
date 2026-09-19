# The Economist

The Economist is a planned Minecraft Java Edition mod about building and living inside a simulated country. Instead of villagers offering fixed, isolated trades, people form households, earn wages, buy necessities, have children, choose jobs, migrate, and respond to the local economy. Players can trade with them directly or hire organized groups to build, farm, mine, and defend settlements.

The goal is not to reproduce every detail of a modern country. The goal is to create believable cause and effect: a mine creates jobs and stone, workers require food and homes, new homes attract families, families raise children, taxes fund guards, shortages raise prices, and war or disaster can disrupt the whole chain.

## Project status

This repository now contains a minimal, modular Fabric foundation. Gameplay systems have not been implemented yet.

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

The remapped development JAR is written to `build/libs/`. The build also runs the module-bootstrap unit tests. The foundation contains no NPC, economy, item, block, service, or simulation content.

### Code organization

- `TheEconomistMod` is the small Fabric composition root.
- `TheEconomistModule` is the contract implemented by future feature packages.
- `ModModules` is the single ordered list of enabled features.
- `ModuleLoader` validates module IDs before initializing features in dependency order.

Future systems should live in focused feature packages and register through a module instead of adding unrelated setup code to the Fabric entry point.

## Design principles

1. **People, not vending machines.** Every adult has a household, job, needs, skills, money, and relationships that influence decisions.
2. **Visible causes.** Players should understand why a price changed, a worker resigned, or a family moved.
3. **Useful gameplay first.** Economic realism must create interesting choices rather than repetitive administration.
4. **No free resources.** NPC labor consumes time, tools, food, materials, and wages. Services transform or transport resources instead of creating them.
5. **Local simulation.** Towns have their own inventories, wages, prices, laws, and public budgets.
6. **Scalable simulation.** Nearby citizens use entity AI; distant citizens use lightweight statistical simulation.
7. **Player agency without total control.** Citizens can refuse unfair contracts, change careers, protest, migrate, or defend themselves.

## Player Trade system
- Player will shift right click another player or citizen to start a trade
- A gui will be opened feature two inventory, one on the left(yourself) one on the right (The citizen/player you want to trade with)
- If both accept, the trade will succeed
- Citizen will consider if the trade is worth-it

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

The currency is the **Crown**, available as physical coins and later as bank balances:

- 1 Crown copper coin
- 10 Crown silver coin
- 100 Crown gold coin

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
