# Crown Currency Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three registered Crown items with fixed server-owned values, assets, creative access, and reusable total calculation for the later trade system.

**Architecture:** Keep denomination metadata in a small pure-Java enum, Fabric registration in the currency feature package, and authoritative `ItemStack` valuation in one registry-identity-based service. `CurrencyModule` is the first gameplay module in `ModModules`, and no client-only class is referenced by common initialization.

**Tech Stack:** Java 25, Minecraft 26.3 Mojang mappings, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, JUnit Jupiter 5.13.4, Gradle 9.6

**Spec:** `docs/superpowers/specs/2026-09-19-citizen-secure-trade-foundation-design.md`

## Global Constraints

- Mod ID is exactly `theeconomist`; main package is exactly `com.jedts.theeconomist`.
- Minecraft is exactly 26.3; Fabric Loader is 0.19.5; Fabric API is 0.161.0+26.3; Java release is 25.
- Currency item identifiers are exactly `copper_crown`, `silver_crown`, and `gold_crown`.
- Denomination values are exactly 1, 10, and 100 Crowns; maximum stack size is 64.
- Values come from registered item identity, never display names, lore, or client-supplied numbers.
- Crowns have no recipe, conversion, bank, mint, fee, tax, or survival source in this milestone.
- Common initialization must remain safe on a dedicated server.

## Review Focus

- A renamed or lore-edited ordinary item must value as zero; Task 2 tests registry identity rather than text.
- Large offers can exceed a 32-bit total; Task 1 pins totals to `long` with an overflow-focused test.
- Duplicate IDs or values could make later trade summaries ambiguous; Task 1 tests the complete unique denomination table.
- Missing 26.3 item-definition JSON can produce a purple/black item despite a model file; Task 2 verifies both resource layers for every denomination.
- Registering display code in common initialization can crash a dedicated server; Task 3 runs a dedicated-server launch check and scans common bytecode imports.

---

### Task 1: Define the denomination domain

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/currency/CrownDenomination.java`
- Create: `src/main/java/com/jedts/theeconomist/currency/CrownMath.java`
- Create: `src/test/java/com/jedts/theeconomist/currency/CrownDenominationTest.java`
- Create: `src/test/java/com/jedts/theeconomist/currency/CrownMathTest.java`

**Interfaces:**
- Consumes: no Minecraft runtime classes.
- Produces: `CrownDenomination(String path, int unitValue)`, `CrownDenomination#path()`, `CrownDenomination#unitValue()`, and `CrownMath.total(Map<CrownDenomination, Integer>): long`.

- [ ] **Step 1: Write the denomination tests**

Create `CrownDenominationTest` with the complete expected table and uniqueness assertions:

```java
package com.jedts.theeconomist.currency;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CrownDenominationTest {
    @Test
    void exposesTheFixedDenominationTable() {
        assertEquals(
                Map.of("copper_crown", 1, "silver_crown", 10, "gold_crown", 100),
                Arrays.stream(CrownDenomination.values()).collect(Collectors.toMap(
                        CrownDenomination::path,
                        CrownDenomination::unitValue
                ))
        );
    }

    @Test
    void pathsAndValuesAreUnique() {
        assertEquals(3, Arrays.stream(CrownDenomination.values()).map(CrownDenomination::path).distinct().count());
        assertEquals(3, Arrays.stream(CrownDenomination.values()).map(CrownDenomination::unitValue).distinct().count());
    }
}
```

- [ ] **Step 2: Run the focused test and observe the expected failure**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.currency.CrownDenominationTest"`

Expected: test compilation fails because `CrownDenomination` does not exist.

- [ ] **Step 3: Add the minimal denomination enum**

```java
package com.jedts.theeconomist.currency;

public enum CrownDenomination {
    COPPER("copper_crown", 1),
    SILVER("silver_crown", 10),
    GOLD("gold_crown", 100);

    private final String path;
    private final int unitValue;

    CrownDenomination(String path, int unitValue) {
        this.path = path;
        this.unitValue = unitValue;
    }

    public String path() {
        return path;
    }

    public int unitValue() {
        return unitValue;
    }
}
```

- [ ] **Step 4: Write total-calculation tests, including 32-bit overflow**

```java
package com.jedts.theeconomist.currency;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CrownMathTest {
    @Test
    void totalsMixedDenominations() {
        assertEquals(321L, CrownMath.total(Map.of(
                CrownDenomination.COPPER, 1,
                CrownDenomination.SILVER, 2,
                CrownDenomination.GOLD, 3
        )));
    }

    @Test
    void usesLongArithmetic() {
        assertEquals(214_748_364_700L, CrownMath.total(Map.of(
                CrownDenomination.GOLD, Integer.MAX_VALUE
        )));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> CrownMath.total(Map.of(
                CrownDenomination.COPPER, -1
        )));
    }
}
```

- [ ] **Step 5: Run the math test and observe the expected failure**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.currency.CrownMathTest"`

Expected: test compilation fails because `CrownMath` does not exist.

- [ ] **Step 6: Add the minimal checked calculation**

```java
package com.jedts.theeconomist.currency;

import java.util.Map;
import java.util.Objects;

public final class CrownMath {
    private CrownMath() {
    }

    public static long total(Map<CrownDenomination, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        long total = 0L;
        for (Map.Entry<CrownDenomination, Integer> entry : counts.entrySet()) {
            CrownDenomination denomination = Objects.requireNonNull(entry.getKey(), "denomination");
            int count = Objects.requireNonNull(entry.getValue(), "count");
            if (count < 0) {
                throw new IllegalArgumentException("Crown counts must not be negative");
            }
            total = Math.addExact(total, Math.multiplyExact((long) denomination.unitValue(), count));
        }
        return total;
    }
}
```

- [ ] **Step 7: Run both focused tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.currency.*"`

Expected: both test classes pass with zero failures.

- [ ] **Step 8: Commit the denomination domain**

```powershell
git add src/main/java/com/jedts/theeconomist/currency/CrownDenomination.java src/main/java/com/jedts/theeconomist/currency/CrownMath.java src/test/java/com/jedts/theeconomist/currency
git commit -m "feat: define Crown denominations"
```

### Task 2: Register Crown items and authoritative stack valuation

**Files:**
- Create: `src/main/java/com/jedts/theeconomist/currency/CrownItems.java`
- Create: `src/main/java/com/jedts/theeconomist/currency/CrownValues.java`
- Create: `src/main/java/com/jedts/theeconomist/currency/CurrencyModule.java`
- Create: `src/test/java/com/jedts/theeconomist/currency/CrownValuesTest.java`
- Modify: `src/main/java/com/jedts/theeconomist/core/module/ModModules.java`

**Interfaces:**
- Consumes: `CrownDenomination#path()` and `#unitValue()`, `TheEconomistMod.MOD_ID`, and `TheEconomistModule`.
- Produces: `CrownItems.item(CrownDenomination): Item`, `CrownItems.CURRENCY_TAB`, `CrownValues.valueOf(ItemStack): int`, and `CrownValues.total(Iterable<ItemStack>): long`.

- [ ] **Step 1: Write a registry-backed valuation test**

Create a test that bootstraps `CrownItems.register()`, constructs stacks for all three registered items, and proves an ordinary renamed item is not currency:

```java
@Test
void valuesOnlyRegisteredCrownItems() {
    CrownItems.register();
    assertEquals(1, CrownValues.valueOf(new ItemStack(CrownItems.item(CrownDenomination.COPPER))));
    assertEquals(10, CrownValues.valueOf(new ItemStack(CrownItems.item(CrownDenomination.SILVER))));
    assertEquals(100, CrownValues.valueOf(new ItemStack(CrownItems.item(CrownDenomination.GOLD))));
    assertEquals(0, CrownValues.valueOf(new ItemStack(Items.PAPER)));
}

@Test
void totalsCountsAndIgnoresNonCurrency() {
    ItemStack copper = new ItemStack(CrownItems.item(CrownDenomination.COPPER), 7);
    ItemStack silver = new ItemStack(CrownItems.item(CrownDenomination.SILVER), 3);
    assertEquals(37L, CrownValues.total(List.of(copper, silver, new ItemStack(Items.PAPER, 64))));
}
```

If the vanilla registries require `SharedConstants.tryDetectVersion()` and `Bootstrap.bootStrap()` in this mapping set, place those calls in a `@BeforeAll` method and guard them with a static boolean.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.currency.CrownValuesTest"`

Expected: test compilation fails because `CrownItems` and `CrownValues` do not exist.

- [ ] **Step 3: Implement item registration in one registry owner**

Use one immutable `EnumMap<CrownDenomination, Item>`. For Minecraft 26.3, create each item with an item `ResourceKey` assigned to `Item.Properties` before registry insertion:

```java
private static Item register(CrownDenomination denomination) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TheEconomistMod.MOD_ID, denomination.path());
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
    Item item = new Item(new Item.Properties().setId(key).stacksTo(64));
    return Registry.register(BuiltInRegistries.ITEM, key, item);
}
```

`register()` must be idempotent within one JVM, register the three entries in enum order, then register one creative group whose icon is the Gold Crown and whose display order is Copper, Silver, Gold. `item(denomination)` must throw a clear `IllegalStateException` if called before registration.

- [ ] **Step 4: Implement stack valuation using identity**

```java
public static int valueOf(ItemStack stack) {
    Objects.requireNonNull(stack, "stack");
    for (CrownDenomination denomination : CrownDenomination.values()) {
        if (stack.is(CrownItems.item(denomination))) {
            return denomination.unitValue();
        }
    }
    return 0;
}

public static long total(Iterable<ItemStack> stacks) {
    long total = 0L;
    for (ItemStack stack : stacks) {
        total = Math.addExact(total, Math.multiplyExact((long) valueOf(stack), stack.getCount()));
    }
    return total;
}
```

- [ ] **Step 5: Add the module and ordered module inventory**

`CurrencyModule` implements `TheEconomistModule`, returns ID `currency`, and calls only `CrownItems.register()`. Replace the empty `ModModules` list with:

```java
private static final List<TheEconomistModule> MODULES = List.of(
        new CurrencyModule()
);
```

- [ ] **Step 6: Run the currency and module tests**

Run: `./gradlew.bat test --tests "com.jedts.theeconomist.currency.*" --tests "com.jedts.theeconomist.core.module.ModuleLoaderTest"`

Expected: all focused tests pass. If a mapping signature differs, inspect the 26.3 remapped JAR and change only the Fabric adapter; do not change the public interfaces above.

- [ ] **Step 7: Commit item registration and valuation**

```powershell
git add src/main/java/com/jedts/theeconomist/currency src/test/java/com/jedts/theeconomist/currency src/main/java/com/jedts/theeconomist/core/module/ModModules.java
git commit -m "feat: register Crown currency items"
```

### Task 3: Add Crown resources, tooltip text, and acceptance checks

**Files:**
- Create: `src/main/resources/assets/theeconomist/lang/en_us.json`
- Create: `src/main/resources/assets/theeconomist/items/copper_crown.json`
- Create: `src/main/resources/assets/theeconomist/items/silver_crown.json`
- Create: `src/main/resources/assets/theeconomist/items/gold_crown.json`
- Create: `src/main/resources/assets/theeconomist/models/item/copper_crown.json`
- Create: `src/main/resources/assets/theeconomist/models/item/silver_crown.json`
- Create: `src/main/resources/assets/theeconomist/models/item/gold_crown.json`
- Create: `src/main/resources/assets/theeconomist/textures/item/copper_crown.png`
- Create: `src/main/resources/assets/theeconomist/textures/item/silver_crown.png`
- Create: `src/main/resources/assets/theeconomist/textures/item/gold_crown.png`
- Create: `src/main/resources/data/theeconomist/tags/item/currency.json`
- Modify: `src/main/java/com/jedts/theeconomist/currency/CrownItems.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: registered Crown IDs and `CrownDenomination#unitValue()`.
- Produces: localized names, value tooltips, item models/textures, `#theeconomist:currency`, creative access, and user-facing documentation.

- [ ] **Step 1: Add localization and explicit tooltip keys**

Create `en_us.json` with these exact keys:

```json
{
  "item.theeconomist.copper_crown": "Copper Crown",
  "item.theeconomist.silver_crown": "Silver Crown",
  "item.theeconomist.gold_crown": "Gold Crown",
  "itemGroup.theeconomist.currency": "The Economist",
  "tooltip.theeconomist.crown_value.one": "Value: 1 Crown",
  "tooltip.theeconomist.crown_value.many": "Value: %s Crowns"
}
```

Use a `CrownItem` subclass or the 26.3 item-component/tooltip API so the Copper item selects the singular key and Silver/Gold select the plural key with their server-defined value. The text is descriptive only; `CrownValues` remains authoritative.

- [ ] **Step 2: Add both 26.3 item-definition and baked-model JSON files**

Each `assets/theeconomist/items/<name>.json` points to its model:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "theeconomist:item/copper_crown"
  }
}
```

Each `assets/theeconomist/models/item/<name>.json` uses the generated-item parent:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "theeconomist:item/copper_crown"
  }
}
```

Repeat with the exact Silver and Gold identifiers rather than sharing a mismatched model.

- [ ] **Step 3: Add deterministic 16-by-16 coin textures**

Create three transparent PNGs with the same circular Crown silhouette and denomination-specific palettes: copper `#B87333`, silver `#C0C0C0`, and gold `#FFD700`, plus darker edge pixels for contrast. Confirm every file is exactly 16 by 16 pixels and uses RGBA color.

- [ ] **Step 4: Add the currency item tag**

```json
{
  "replace": false,
  "values": [
    "theeconomist:copper_crown",
    "theeconomist:silver_crown",
    "theeconomist:gold_crown"
  ]
}
```

- [ ] **Step 5: Add a resource completeness test**

Create a JUnit parameterized test over `CrownDenomination.values()` that loads `/assets/theeconomist/items/<path>.json`, `/assets/theeconomist/models/item/<path>.json`, and `/assets/theeconomist/textures/item/<path>.png`; parse both JSON resources; assert the PNG dimensions are 16 by 16; and assert the tag lists all three full identifiers exactly once.

- [ ] **Step 6: Run tests and build**

Run:

```powershell
./gradlew.bat test --tests "com.jedts.theeconomist.currency.*"
./gradlew.bat build
```

Expected: all tests pass and the remapped JAR contains all nine per-item asset files, the language file, and the currency tag.

- [ ] **Step 7: Launch a dedicated server smoke test**

Run the Loom `runServer` configuration with `eula=true`, wait for the server-ready line, then issue `stop`. Search the log for `ClassNotFoundException`, `NoClassDefFoundError`, missing registry entries, and client-package references.

Expected: all three items register and the server starts without loading any `net.minecraft.client` class.

- [ ] **Step 8: Verify the items in a development client**

Run `./gradlew.bat runClient`, open the mod creative group, and verify names, value tooltips, stack size 64, models, and textures. Use `/give @s theeconomist:copper_crown 64` and the corresponding Silver and Gold commands.

- [ ] **Step 9: Update README milestone status**

Under Project status, state that Crown currency is implemented and obtainable only through Creative or `/give`. Under Money, property, and trade, document the three exact item IDs and state that crafting, conversion, and survival sources are outside this milestone.

- [ ] **Step 10: Commit resources and documentation**

```powershell
git add src/main/resources src/main/java/com/jedts/theeconomist/currency src/test/java/com/jedts/theeconomist/currency README.md
git commit -m "feat: add Crown assets and documentation"
```

- [ ] **Step 11: Record acceptance evidence**

Run `./gradlew.bat clean build`, confirm zero failed tests, inspect the remapped JAR, and record the exact client and dedicated-server smoke-test results in the implementation handoff.
