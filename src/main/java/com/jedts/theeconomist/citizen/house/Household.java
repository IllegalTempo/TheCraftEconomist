package com.jedts.theeconomist.citizen.house;

import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

/** Persistent residents assigned to one generated house anchor. */
public final class Household {
    private final String dimensionId;
    private final BlockPos anchor;
    private final String surname;
    private final int residentCount;
    private final Runnable issuedChanged;
    private int issuedSlots;
    private final java.util.ArrayList<String> givenNames;
    private final java.util.Map<String, BlockPos> targetLocations;
    private BlockPos storagePosition;

    Household(String dimensionId, BlockPos anchor, String surname, int residentCount, int issuedSlots, List<String> givenNames, java.util.Map<String, BlockPos> targetLocations, BlockPos storagePosition, Runnable issuedChanged) {
        if (dimensionId == null || dimensionId.isBlank())
            throw new IllegalArgumentException("dimensionId must not be blank");
        this.dimensionId = dimensionId;
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.surname = requireSurname(surname);
        if (residentCount < 2 || residentCount > 4)
            throw new IllegalArgumentException("residentCount must be between 2 and 4");
        int validSlots = (1 << residentCount) - 1;
        if ((issuedSlots & ~validSlots) != 0)
            throw new IllegalArgumentException("issued slots exceed resident count");
        this.residentCount = residentCount;
        this.issuedSlots = issuedSlots;
        if (givenNames.size() > residentCount) throw new IllegalArgumentException("too many reserved names");
        this.givenNames = new java.util.ArrayList<>(givenNames);
        while (this.givenNames.size() < residentCount) this.givenNames.add("");
        this.targetLocations = new java.util.HashMap<>();
        targetLocations.forEach((key, pos) -> { if (key != null && !key.isBlank() && pos != null) this.targetLocations.put(key, pos.immutable()); });
        this.storagePosition = storagePosition == null ? null : storagePosition.immutable();
        this.issuedChanged = Objects.requireNonNull(issuedChanged, "issuedChanged");
    }

    public BlockPos anchor() {
        return anchor;
    }

    public java.util.Optional<BlockPos> storagePosition() { return java.util.Optional.ofNullable(storagePosition); }

    public void assignStoragePosition(BlockPos position) {
        BlockPos value = Objects.requireNonNull(position, "position").immutable();
        if (!value.equals(storagePosition)) {
            storagePosition = value;
            issuedChanged.run();
        }
    }

    public String surname() {
        return surname;
    }

    public int residentCount() {
        return residentCount;
    }

    public List<Integer> unissuedSlots() {
        return IntStream.range(0, residentCount).filter(slot -> (issuedSlots & (1 << slot)) == 0).boxed().toList();
    }

    /** Records a resident slot once. A recorded slot is never made available again. */
    public boolean issue(int slot) {
        validateSlot(slot);
        int slotMask = 1 << slot;
        if ((issuedSlots & slotMask) != 0) return false;
        issuedSlots |= slotMask;
        issuedChanged.run();
        return true;
    }

    /** Retains the actual issued identity, including recovery of older entries from loaded residents. */
    public void rememberGivenName(int slot, String givenName) {
        validateSlot(slot);
        if (givenName == null || givenName.isBlank()) throw new IllegalArgumentException("givenName must not be blank");
        if (givenNames.get(slot).isEmpty()) {
            givenNames.set(slot, givenName);
            issuedChanged.run();
        }
    }

    public java.util.Set<String> usedGivenNames() {
        return givenNames.stream().filter(name -> !name.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<String> residentDisplayNames() {
        return givenNames.stream().filter(name -> !name.isBlank()).map(name -> name + " " + surname).toList();
    }

    List<String> givenNames() {
        return List.copyOf(givenNames);
    }

    public java.util.Optional<BlockPos> targetLocation(String actionId) { return java.util.Optional.ofNullable(targetLocations.get(actionId)); }
    public java.util.Map<String, BlockPos> targetLocations() { return java.util.Map.copyOf(targetLocations); }
    public void rememberTarget(String actionId, BlockPos pos) {
        if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("actionId must not be blank");
        targetLocations.put(actionId, java.util.Objects.requireNonNull(pos).immutable()); issuedChanged.run();
    }
    public void forgetTarget(String actionId) { if (targetLocations.remove(actionId) != null) issuedChanged.run(); }

    public UUID slotId(int slot) {
        validateSlot(slot);
        return UUID.nameUUIDFromBytes((dimensionId + ":" + anchor.asLong() + ":" + slot)
                .getBytes(StandardCharsets.UTF_8));
    }

    public UUID householdId() {
        return UUID.nameUUIDFromBytes((dimensionId + ":" + anchor.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    public List<UUID> issuedResidentIds() {
        return IntStream.range(0, residentCount).filter(slot -> (issuedSlots & (1 << slot)) != 0)
                .mapToObj(this::slotId).toList();
    }

    int issuedSlots() {
        return issuedSlots;
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= residentCount)
            throw new IllegalArgumentException("slot must be between 0 and " + (residentCount - 1));
    }

    private static String requireSurname(String surname) {
        if (surname == null || surname.isBlank()) throw new IllegalArgumentException("surname must not be blank");
        return surname;
    }
}

