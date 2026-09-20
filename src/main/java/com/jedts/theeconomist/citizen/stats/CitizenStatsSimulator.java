package com.jedts.theeconomist.citizen.stats;

/** Pure one-interval needs transition; world adapters decide whether inputs are true. */
public final class CitizenStatsSimulator {
    private CitizenStatsSimulator() {
    }

    public static CitizenStats advance(CitizenStats stats, boolean ate, boolean rested, boolean safe) {
        int hunger = stats.hunger() + (ate ? 20 : -2);
        int energy = stats.energy() + (rested ? 15 : -2);
        int safety = stats.safety() + (safe ? 2 : -5);
        boolean deprived = hunger < 20 || energy < 20 || safety < 30;
        int morale = stats.morale() + (deprived ? -5 : 2);
        int anger = stats.anger() + (deprived ? 3 : -2);
        return new CitizenStats(hunger, energy, safety, morale, stats.intelligence(), anger,
                stats.education(), stats.ambition(), stats.thrift(), stats.bravery(),
                stats.sociability(), stats.loyalty());
    }
}
