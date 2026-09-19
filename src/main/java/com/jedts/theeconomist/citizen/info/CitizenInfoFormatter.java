package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.identity.CitizenIdentity;

import java.util.Locale;

public final class CitizenInfoFormatter {
    private CitizenInfoFormatter() {
    }

    public static String format(CitizenIdentity identity, float health, float maxHealth, double movementSpeed,
                                double followRange, int x, int y, int z) {
        String profile = identity.profileUsername().orElse("none");
        return "Citizen: " + identity.displayName()
                + " | ID: " + identity.citizenId()
                + " | Life stage: " + identity.lifeStage()
                + " | Model: " + identity.appearance().modelType()
                + " | Profile: " + profile
                + " | Health: " + format(health) + "/" + format(maxHealth)
                + " | Movement speed: " + format(movementSpeed)
                + " | Follow range: " + format(followRange)
                + " | Position: " + x + ", " + y + ", " + z;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
