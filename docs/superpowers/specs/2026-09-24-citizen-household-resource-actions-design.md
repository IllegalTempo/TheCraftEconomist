# Citizen Household Resource Actions Design

Date: 2026-09-24
Status: In-chat design approved; awaiting written spec review

## Objective

Let Citizens fulfill household work that requires crafted items by finding missing resources, gathering them, sharing them through a chest in their home, crafting intermediate and final items, and placing the result. The first consumer is a composter action for the household farm.

## Approved requirements

- Crafting materials are shared by the household through a physical chest in its home.
- A Citizen's own inventory is used to carry materials while gathering, depositing, withdrawing, or crafting; it is not the long-term household store.
- Provide reusable actions such as `Find X` and `Craft X`, so behaviors compose generic item requests rather than embedding item-specific acquisition sequences.
- The first requested item is one composter for the household farm.
- If the home does not contain a suitable shared chest, the system must establish or locate one before treating household materials as available.
- Use registered Minecraft crafting recipes for craftable items and support intermediate crafting requirements, including a crafting table.
- Use the existing household target memory and incremental search behavior to find world resources. Validate a remembered resource location before relying on it; clear stale memory and resume searching when it is gone.
- Keep the resource acquisition framework reusable for later item requests.

## Architecture

### Reusable item actions

Expose composable, server-side actions that a behavior can invoke with an item request and quantity:

- **Find item:** Check the household chest for the requested quantity. If it is short, search for an eligible source, travel there, gather the resource, and deposit it in the chest. If there is no household, use the Citizen's saved inventory as the storage fallback.
- **Craft item:** Check for an existing requested item first. Resolve a registered crafting recipe and its ingredients, request missing ingredients through the find-item action, retrieve the required ingredients, craft the requested quantity, and store the output in the household chest. Crafting intermediate items uses the same action recursively, with cycle and depth protection.
- **Place item:** For the initial composter use, take one crafted composter from the household chest and place it at an eligible site near the household farm. This is a generic placement action that future behaviors can reuse with their own site finder.

An action reports progress and a terminal result (complete, still working, unavailable, or failed). Its caller supplies the requested item, quantity, and any action-specific placement or resource eligibility rules. Behaviors remain responsible for deciding when the request is relevant; the resource actions own the repeatable find, craft, and placement steps.

### Household chest and materials

The chest block in the generated home is the durable shared inventory; vanilla block-entity persistence saves its contents. The first implementation must inspect the existing house template and define a stable way to find the household chest. If there is no chest, setup places or assigns one in a safe home location. It must not silently treat an arbitrary nearby player chest as household storage.

Resource actions move items through a Citizen's saved inventory while working. Before moving items, they verify chest contents, inventory capacity, and that the target chest is still valid and accessible. If storage is missing, blocked, or full, the action reports that state and does not destroy gathered resources. Items are not created or removed except through world gathering, recipe output, and explicit transfers between the chest and Citizen inventory.

If existing player-created Citizens lack a household chest, their own saved inventory is the compatibility fallback. Generated households use the shared home chest.

### Recipe resolution and resource sources

The crafting action resolves current recipes through Minecraft's recipe manager rather than hardcoding composter ingredients. It plans only recipes whose inputs can be supplied from the household chest or acquired through registered resource providers. A recipe search is bounded, detects cycles, and never loops on an unavailable ingredient.

Resource providers define how a missing item can be obtained in the world. The first provider supports natural wood logs needed for the composter chain. It uses the shared target search service with a stable item/provider key, checks the nearby area before family memory, searches incrementally within the existing 256-block home radius, and never force-loads chunks. The provider validates the block again before harvesting and forgets stale family memory when that location no longer contains a usable log. Additional providers can later support other renewable or mineable resources without changing recipe planning.

Crafting uses actual recipe inputs and output counts. For recipes requiring a crafting table, the action first ensures the household has one, places it in an eligible home location if needed, and performs the recipe there. Crafting must not consume inputs until the recipe and output capacity have been checked.

### Household coordination

Because all family members use one chest, a household-scoped work claim coordinates active find/craft/place requests. Only the current claimant manipulates a given household request at a time. The claim is released on completion, cancellation, invalidation, or Citizen unload/death so another resident can continue. Chest contents remain the durable record of gathered and crafted materials; transient claims and plans can be rebuilt after reload.

The composter request is household-scoped and requires at most one usable composter near the farm. The action checks for an existing composter before gathering or crafting, claims the request, and finishes after a successful placement. This avoids family members independently crafting duplicates.

### Composter behavior flow

The behavior requests one composter for its household farm. The generic actions then:

1. Search for an existing usable composter near the farm; finish if one exists.
2. Claim the household request and check for crafting materials in the home chest.
3. Find and harvest missing natural logs, returning them to the chest.
4. Craft and store a crafting table if one is not already available; place it at the home crafting site.
5. Use the registered recipes to make planks, slabs, and a composter as required, placing outputs in the shared chest between steps.
6. Place the composter near the farm and release the household claim.

The behavior displays the active reusable action's status, such as `Finding oak logs`, `Storing logs`, `Crafting slabs`, or `Placing composter`.

## Persistence and failure handling

- Chest contents use the chest's normal world save.
- Existing household target positions remain in `HouseholdSavedData`; a stable action key separates wood-resource targets from other remembered targets.
- Existing per-Citizen inventory and search cursors continue to save transit items and individual search progress.
- Active plans are reconstructed from the requested output and current chest contents after reload; do not serialize live recipe or world references.
- Claims are released when a resident can no longer work. A stale claim must not permanently block the family after unload or crash.
- A missing chest, full chest, full Citizen inventory, unavailable recipe, unavailable resource provider, unreachable site, failed harvest, or invalidated target produces a visible status and allows retry or another household member to continue.
- Before harvesting, transferring, crafting, or placing, revalidate the block/container and capacity at the target.
- Search and crafting work is incremental and bounded per tick. Do not force-load chunks.

## Verification approach

Implementation verification should cover the generic find and craft actions, recipe dependency resolution with cycles and missing providers, real chest transfers, saving and reloading chest contents, target memory invalidation, bounded log searches, item conservation, chest and Citizen capacity limits, claim release and takeover, crafting table setup, and one-composter household completion without duplicate crafting. A manual game check should follow a family from an empty home chest through log gathering, intermediate crafting, and composter placement near its farm.

## Out of scope

- A general market, player-item purchasing, or trading integration for recipe inputs.
- Arbitrary loot-container search, stealing from player storage, or force-loading distant resource chunks.
- A universal recipe planner for every possible world acquisition method on the first implementation; resource providers are added as needs arise.
- Personal crafting requests that bypass household storage for generated family members.
- Producing multiple composters after one usable household composter has been placed.
