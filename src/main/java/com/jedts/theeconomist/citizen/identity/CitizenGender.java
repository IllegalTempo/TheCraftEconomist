package com.jedts.theeconomist.citizen.identity;

import java.util.UUID;

/** Binary sex presentation used by the current Citizen model. */
public enum CitizenGender {
    MALE, FEMALE;

    public static CitizenGender forFallback(UUID citizenId) {
        if (citizenId == null) throw new NullPointerException("citizenId");
        return (citizenId.hashCode() & 1) == 0 ? MALE : FEMALE;
    }
}
