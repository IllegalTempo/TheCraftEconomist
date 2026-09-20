package com.jedts.theeconomist.citizen.stats;

public record CitizenSkills(int farming, int mining, int building, int combat, int trade) {
    public CitizenSkills {
        farming = clamp(farming);
        mining = clamp(mining);
        building = clamp(building);
        combat = clamp(combat);
        trade = clamp(trade);
    }

    public static CitizenSkills defaults() {
        return new CitizenSkills(0, 0, 0, 0, 0);
    }

    public CitizenSkills train(CitizenSkill skill, int amount) {
        if (amount < 0) throw new IllegalArgumentException("training amount must not be negative");
        return switch (skill) {
            case FARMING -> new CitizenSkills(farming + amount, mining, building, combat, trade);
            case MINING -> new CitizenSkills(farming, mining + amount, building, combat, trade);
            case BUILDING -> new CitizenSkills(farming, mining, building + amount, combat, trade);
            case COMBAT -> new CitizenSkills(farming, mining, building, combat + amount, trade);
            case TRADE -> new CitizenSkills(farming, mining, building, combat, trade + amount);
        };
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
