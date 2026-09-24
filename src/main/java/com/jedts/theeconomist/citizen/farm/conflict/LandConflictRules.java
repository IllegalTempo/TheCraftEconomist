package com.jedts.theeconomist.citizen.farm.conflict;

public final class LandConflictRules {
    private LandConflictRules() { }

    public static double fightChance(int anger, int plotBlocks, int unclaimedCount,
                                     double citizenHealth, double opponentHealth,
                                     double citizenArmor, double opponentArmor, int wheatPrice) {
        double scarcity = 1.0 - clamp(unclaimedCount, 0, 64) / 64.0;
        double expectedValue = Math.min(Math.max(0, plotBlocks), 64) * Math.max(1, wheatPrice);
        double threat = clamp((Math.max(0, opponentHealth) + 2 * Math.max(0, opponentArmor))
                / Math.max(1, Math.max(0, citizenHealth) + 2 * Math.max(0, citizenArmor)), 0, 2);
        double netValue = clamp((expectedValue - 8 * threat) / 128.0, 0, 1);
        return clamp(0.05 + 0.45 * clamp(anger, 0, 100) / 100.0 + 0.25 * netValue + 0.20 * scarcity,
                0.05, 0.95);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
