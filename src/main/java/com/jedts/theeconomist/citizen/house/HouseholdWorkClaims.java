package com.jedts.theeconomist.citizen.house;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived leases that serialize household work using shared supplies. */
public final class HouseholdWorkClaims {
    private static final long LEASE_TICKS = 100;
    private static final Map<Key, Claim> CLAIMS = new HashMap<>();

    private HouseholdWorkClaims() { }

    public static boolean tryClaim(UUID householdId, String requestId, UUID citizenId, long gameTime) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        Key key = key(householdId, requestId);
        Claim current = CLAIMS.get(key);
        if (current != null && current.expiresAt() > gameTime && !current.citizenId().equals(citizenId)) return false;
        CLAIMS.put(key, new Claim(citizenId, gameTime + LEASE_TICKS));
        return true;
    }

    public static boolean renew(UUID householdId, String requestId, UUID citizenId, long gameTime) {
        Key key = key(householdId, requestId);
        Claim current = CLAIMS.get(key);
        if (current == null || current.expiresAt() <= gameTime || !current.citizenId().equals(citizenId)) return false;
        CLAIMS.put(key, new Claim(citizenId, gameTime + LEASE_TICKS));
        return true;
    }

    public static void release(UUID householdId, String requestId, UUID citizenId) {
        Key key = key(householdId, requestId);
        Claim current = CLAIMS.get(key);
        if (current != null && current.citizenId().equals(citizenId)) CLAIMS.remove(key);
    }

    private static Key key(UUID householdId, String requestId) {
        if (householdId == null) throw new IllegalArgumentException("householdId must not be null");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId must not be blank");
        return new Key(householdId, requestId);
    }

    private record Key(UUID householdId, String requestId) { }
    private record Claim(UUID citizenId, long expiresAt) { }
}
