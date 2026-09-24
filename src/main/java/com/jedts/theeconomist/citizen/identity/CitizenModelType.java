package com.jedts.theeconomist.citizen.identity;

import java.util.UUID;

public enum CitizenModelType {
    WIDE, SLIM;

    public static CitizenModelType forFallback(UUID citizenId) {
        if (citizenId == null) {
            throw new NullPointerException("citizenId");
        }
        return (citizenId.hashCode() & 1) == 0 ? WIDE : SLIM;
    }
}
