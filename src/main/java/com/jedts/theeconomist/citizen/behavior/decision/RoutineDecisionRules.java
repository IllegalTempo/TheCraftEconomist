package com.jedts.theeconomist.citizen.behavior.decision;

import com.jedts.theeconomist.citizen.config.DecisionScoringConfig;

import java.util.Locale;

/** Pure live-state mappings used by home, work, and rest evaluations. */
public final class RoutineDecisionRules {
    private RoutineDecisionRules() { }

    public static CitizenActionEvaluation sleep(boolean night, boolean household, int energy,
                                                boolean bedAvailable, double distance) {
        return sleep(night, household, energy, bedAvailable, distance, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation sleep(boolean night, boolean household, int energy,
                                                boolean bedAvailable, double distance, DecisionScoringConfig config) {
        if (!night) return CitizenBehaviorEvaluation.unavailable("sleep", "Sleep", "It is not sleeping time");
        if (!household) return CitizenBehaviorEvaluation.unavailable("sleep", "Sleep", "No generated household");
        double tiredness = 1.0 - unit(energy / config.statScale());
        String reason = bedAvailable ? String.format(Locale.ROOT, "Energy %d/%d; bed available", energy, (int)config.statScale())
                : String.format(Locale.ROOT, "Energy %d/%d; no free reachable bed", energy, (int)config.statScale());
        return CitizenBehaviorEvaluation.score("sleep", "Sleep", true,
                new CitizenScoreInputs(v(config,"sleep","urgencyBase") + v(config,"sleep","urgencyTiredness") * tiredness,
                        v(config,"sleep","benefit"), v(config,"sleep","capability"),
                        bedAvailable ? v(config,"sleep","bedOpportunity") : 0.0,
                        v(config,"sleep","costBase") + v(config,"sleep","costDistance")
                                * unit(distance / v(config,"sleep","sleepCostDistanceScale")),
                        v(config,"sleep","risk")), reason, false, config);
    }

    public static CitizenActionEvaluation confused(boolean night, boolean missingHousehold, boolean missingHouseholdData) {
        return confused(night, missingHousehold, missingHouseholdData, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation confused(boolean night, boolean missingHousehold,
                                                    boolean missingHouseholdData, DecisionScoringConfig config) {
        if (!night || !missingHousehold && !missingHouseholdData)
            return CitizenBehaviorEvaluation.unavailable("confused", "Wait for daytime", "Household and home are available");
        String reason = missingHousehold ? "Night-time; household record is missing"
                : "Night-time; home or household ledger entry is missing";
        return CitizenBehaviorEvaluation.score("confused", "Wait for daytime", true,
                new CitizenScoreInputs(v(config,"confused","urgency"), v(config,"confused","benefit"),
                        v(config,"confused","capability"), v(config,"confused","opportunity"),
                        v(config,"confused","cost"), v(config,"confused","risk")), reason, false, config);
    }

    public static CitizenActionEvaluation farming(boolean canWork, boolean day, boolean hasHome, boolean withinWorkArea,
                                                   int ambition, int farmingSkill, boolean targetAvailable,
                                                   boolean hasHoe, boolean hasSeeds, int freeSlots) {
        return farming(canWork, day, hasHome, withinWorkArea, ambition, farmingSkill, targetAvailable, hasHoe,
                hasSeeds, freeSlots, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation farming(boolean canWork, boolean day, boolean hasHome, boolean withinWorkArea,
                                                   int ambition, int farmingSkill, boolean targetAvailable,
                                                   boolean hasHoe, boolean hasSeeds, int freeSlots,
                                                   DecisionScoringConfig config) {
        if (!day) return CitizenBehaviorEvaluation.unavailable("farmer", "Farm", "It is outside work hours");
        if (!canWork) return CitizenBehaviorEvaluation.unavailable("farmer", "Farm", "Citizen cannot work at this life stage");
        if (!hasHome) return CitizenBehaviorEvaluation.unavailable("farmer", "Farm", "No home work area");
        if (!withinWorkArea) return CitizenBehaviorEvaluation.unavailable("farmer", "Farm",
                "Outside the " + (int)v(config, "farmer", "workRadius") + "-block home work area");
        double tools = hasHoe ? 1.0 : 0.0;
        double storage = freeSlots > 0 ? 1.0 : 0.0;
        double opportunity = targetAvailable && hasSeeds && storage > 0 ? 1.0 : 0.0;
        double capability = (v(config,"farmer","skillBase")
                + v(config,"farmer","skillShare") * unit(farmingSkill / config.statScale())) * tools;
        String reason = !hasHoe ? "No usable hoe" : storage == 0.0 ? "Inventory is full"
                : !hasSeeds ? "No seed source is available" : !targetAvailable
                ? "No reachable farm task" : "Farm task available; ambition " + ambition + "/" + (int)config.statScale();
        return CitizenBehaviorEvaluation.score("farmer", "Farm", true,
                new CitizenScoreInputs(v(config,"farmer","urgencyBase")
                        + v(config,"farmer","urgencyAmbition") * unit(ambition / config.statScale()),
                        v(config,"farmer","benefit"), capability,
                        opportunity > 0 ? v(config,"farmer","opportunity") : 0.0,
                        v(config,"farmer","cost"), v(config,"farmer","risk")), reason, false, config);
    }

    public static CitizenActionEvaluation preparingComposter(boolean canWork, boolean day, boolean household,
                                                               boolean storageAvailable, boolean alreadyExists,
                                                               int ambition, int farmingSkill,
                                                               DecisionScoringConfig config) {
        if (!day) return CitizenBehaviorEvaluation.unavailable("composter", "Make farm composter", "It is outside work hours");
        if (!canWork) return CitizenBehaviorEvaluation.unavailable("composter", "Make farm composter", "Citizen cannot work at this life stage");
        if (!household) return CitizenBehaviorEvaluation.unavailable("composter", "Make farm composter", "No generated household");
        if (!storageAvailable) return CitizenBehaviorEvaluation.unavailable("composter", "Make farm composter", "Home chest is unavailable");
        if (alreadyExists) return CitizenBehaviorEvaluation.unavailable("composter", "Make farm composter", "Household composter already placed");
        double urgency = Math.min(1.0, v(config,"farmer","urgencyBase")
                + v(config,"farmer","urgencyAmbition") * unit(ambition / config.statScale()) + 0.35);
        double capability = v(config,"farmer","skillBase")
                + v(config,"farmer","skillShare") * unit(farmingSkill / config.statScale());
        return CitizenBehaviorEvaluation.score("composter", "Make farm composter", true,
                new CitizenScoreInputs(urgency, 0.9, capability, 1.0,
                        v(config,"farmer","cost"), v(config,"farmer","risk")),
                "Household farm has no composter", false, config);
    }

    public static CitizenActionEvaluation contractReview(boolean day, boolean intervalReady, boolean canWork,
                                                          boolean offerAvailable, boolean activeContract,
                                                          int ambition, long ticksToDeadline) {
        return contractReview(day, intervalReady, canWork, offerAvailable, activeContract, ambition, ticksToDeadline,
                DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation contractReview(boolean day, boolean intervalReady, boolean canWork,
                                                          boolean offerAvailable, boolean activeContract,
                                                          int ambition, long ticksToDeadline, DecisionScoringConfig config) {
        if (!day) return CitizenBehaviorEvaluation.unavailable("contract", "Review contracts", "It is outside work hours");
        if (!intervalReady) return CitizenBehaviorEvaluation.unavailable("contract", "Review contracts", "Review interval has not elapsed");
        boolean canAcceptOffer = offerAvailable && !activeContract;
        double opportunity = canAcceptOffer ? v(config,"contract","offerOpportunity") : v(config,"contract","routineOpportunity");
        double deadlineUrgency = 1.0 - unit(Math.max(0, ticksToDeadline) / v(config,"contract","deadlineScale"));
        double capability = canWork ? v(config,"contract","capabilityBase")
                + v(config,"contract","capabilityAmbition") * unit(ambition / config.statScale())
                : v(config,"contract","capabilityCannotWork");
        String reason = canAcceptOffer
                ? String.format(Locale.ROOT, "Eligible contract offer; deadline in %d ticks", ticksToDeadline)
                : activeContract ? "Already working on a contract; routine review due"
                : "No eligible open offer; routine review due";
        return CitizenBehaviorEvaluation.score("contract", "Review contracts", true,
                new CitizenScoreInputs(v(config,"contract","urgencyBase")
                        + v(config,"contract","urgencyAmbition") * unit(ambition / config.statScale())
                        + v(config,"contract","urgencyDeadline") * deadlineUrgency,
                        v(config,"contract","benefit"), capability, opportunity,
                        v(config,"contract","cost"), v(config,"contract","risk")), reason, false, config);
    }

    public static CitizenActionEvaluation returnHome(boolean hasHome, double distance, int safety,
                                                    int energy, boolean night) {
        return returnHome(hasHome, distance, safety, energy, night, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation returnHome(boolean hasHome, double distance, int safety,
                                                    int energy, boolean night, DecisionScoringConfig config) {
        if (!hasHome) return CitizenBehaviorEvaluation.unavailable("return_home", "Return home", "No home is assigned");
        if (distance <= v(config,"return_home","returnHomeRadius")) return CitizenBehaviorEvaluation.unavailable("return_home", "Return home", "Already near home");
        double travel = unit(distance / v(config,"return_home","homeDistanceScale"));
        double danger = 1.0 - unit(safety / config.statScale());
        return CitizenBehaviorEvaluation.score("return_home", "Return home", true,
                new CitizenScoreInputs(v(config,"return_home","urgencyBase") + v(config,"return_home","urgencyTravel") * travel
                        + v(config,"return_home","urgencyDanger") * danger + v(config,"return_home","urgencyNight") * (night ? 1 : 0),
                        night ? v(config,"return_home","nightBenefit") : v(config,"return_home","dayBenefit"),
                        v(config,"return_home","capability"), v(config,"return_home","opportunity"),
                        v(config,"return_home","costBase") + v(config,"return_home","costTravel") * travel,
                        v(config,"return_home","riskDanger") * danger),
                String.format(Locale.ROOT, "Home is %.1f blocks away; safety %d/%d; %s",
                        distance, safety, (int)config.statScale(), night ? "night approaching" : "daytime"), false, config);
    }

    private static double v(DecisionScoringConfig config, String action, String key) { return config.value(action, key); }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
