package com.jedts.theeconomist.citizen.identity;

import java.util.Optional;

/** A Citizen's relationship to the household that spawned them. */
public enum CitizenFamilyRole {
    NONE,
    MOM,
    DAD,
    CHILD;

    public static CitizenFamilyRole forHouseholdSlot(int slot) {
        if (slot < 0) throw new IllegalArgumentException("slot must not be negative");
        return switch (slot) {
            case 0 -> MOM;
            case 1 -> DAD;
            default -> CHILD;
        };
    }

    public Optional<CitizenGender> requiredGender() {
        return switch (this) {
            case MOM -> Optional.of(CitizenGender.FEMALE);
            case DAD -> Optional.of(CitizenGender.MALE);
            case NONE, CHILD -> Optional.empty();
        };
    }
}
