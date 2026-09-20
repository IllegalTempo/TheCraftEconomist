package com.jedts.theeconomist.citizen.stats;

/** Persistent bounded social, need, and trait values for one Citizen. */
public record CitizenStats(int hunger, int energy, int safety, int morale, int intelligence, int anger,
                           int education, int ambition, int thrift, int bravery, int sociability, int loyalty) {
    public CitizenStats {
        hunger = clamp(hunger);
        energy = clamp(energy);
        safety = clamp(safety);
        morale = clamp(morale);
        intelligence = clamp(intelligence);
        anger = clamp(anger);
        education = clamp(education);
        ambition = clamp(ambition);
        thrift = clamp(thrift);
        bravery = clamp(bravery);
        sociability = clamp(sociability);
        loyalty = clamp(loyalty);
    }

    public static CitizenStats defaults() {
        return new CitizenStats(100, 100, 50, 50, 50, 0, 0, 50, 50, 50, 50, 50);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
