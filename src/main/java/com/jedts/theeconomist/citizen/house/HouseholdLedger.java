package com.jedts.theeconomist.citizen.house;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Dimension-local household registry keyed by generated house anchor. */
public final class HouseholdLedger {
    private final String dimensionId;
    private final Map<BlockPos, Household> households = new HashMap<>();
    private final Runnable changed;

    public HouseholdLedger(String dimensionId) {
        this(dimensionId, List.of(), () -> { });
    }

    HouseholdLedger(String dimensionId, List<Entry> entries, Runnable changed) {
        if (dimensionId == null || dimensionId.isBlank())
            throw new IllegalArgumentException("dimensionId must not be blank");
        this.dimensionId = dimensionId;
        this.changed = Objects.requireNonNull(changed, "changed");
        for (Entry entry : entries) {
            Household household = new Household(dimensionId, entry.anchor(), entry.surname(), entry.residentCount(),
                    entry.issuedSlots(), entry.givenNames(), entry.targetLocations(), entry.storagePosition(), this.changed);
            if (households.putIfAbsent(household.anchor(), household) != null)
                throw new IllegalArgumentException("duplicate household anchor: " + household.anchor());
        }
    }

    public String dimensionId() {
        return dimensionId;
    }

    public Optional<Household> get(BlockPos anchor) {
        return Optional.ofNullable(households.get(Objects.requireNonNull(anchor, "anchor")));
    }

    public List<UUID> residentIds(UUID householdId) {
        Objects.requireNonNull(householdId, "householdId");
        return households.values().stream().filter(household -> household.householdId().equals(householdId))
                .findFirst().map(Household::issuedResidentIds).orElse(List.of());
    }

    /** Creates the one persistent household for an anchor, selecting from the current names only when new. */
    public Household getOrCreate(BlockPos anchor, List<String> familyNames, RandomGenerator random) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(familyNames, "familyNames");
        Objects.requireNonNull(random, "random");
        Household existing = households.get(anchor);
        if (existing != null) return existing;
        List<String> validNames = familyNames.stream().filter(name -> name != null && !name.isBlank()).toList();
        if (validNames.isEmpty()) throw new IllegalArgumentException("familyNames must contain a nonblank surname");
        Household household = new Household(dimensionId, anchor, validNames.get(random.nextInt(validNames.size())),
                2 + random.nextInt(3), 0, List.of(), Map.of(), null, changed);
        households.put(household.anchor(), household);
        changed.run();
        return household;
    }

    List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Household household : households.values()) {
            entries.add(new Entry(household.anchor(), household.surname(), household.residentCount(), household.issuedSlots(), household.givenNames(), household.targetLocations(), household.storagePosition().orElse(null)));
        }
        return List.copyOf(entries);
    }

    record Entry(BlockPos anchor, String surname, int residentCount, int issuedSlots, List<String> givenNames, Map<String, BlockPos> targetLocations, BlockPos storagePosition) {
        Entry {
            targetLocations = Map.copyOf(targetLocations);
            storagePosition = storagePosition == null ? null : storagePosition.immutable();
        }
    }
}

