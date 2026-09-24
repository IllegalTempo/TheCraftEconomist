package com.jedts.theeconomist.citizen.info;

import java.util.Optional;

public record CitizenInfoScreenData(String name, String lifeStage, String gender, String familyRole,
                                    String familyMembers, String activeBehavior, String behaviorStatus,
                                    String model, String profile,
                                    String health, String movementSpeed, String followRange, String position,
                                    String hunger, String energy, String safety, String morale,
                                    String intelligence, String anger, String education,
                                    String occupation, String employer, String wage, String workHours,
                                    String contractStatus, String contractTarget, String contractBounty,
                                    String contractDeadline, Optional<CitizenInfoDetails> details,
                                    java.util.List<CitizenDecisionView> decisions,
                                    Optional<CitizenCraftingPreview> craftingPreview) {
    public CitizenInfoScreenData {
        decisions = java.util.List.copyOf(decisions);
        craftingPreview = craftingPreview == null ? Optional.empty() : craftingPreview;
    }
    public CitizenInfoScreenData(String name, String lifeStage, String gender, String familyRole,
                                 String familyMembers, String model, String profile,
                                 String health, String movementSpeed, String followRange, String position,
                                 String hunger, String energy, String safety, String morale,
                                 String intelligence, String anger, String education) {
        this(name, lifeStage, gender, familyRole, familyMembers, "idle", "", model, profile, health, movementSpeed, followRange,
                position, hunger, energy, safety, morale, intelligence, anger, education,
                "UNEMPLOYED", "", "0", "", "NONE", "", "0", "", Optional.empty(), java.util.List.of(), Optional.empty());
    }

    public CitizenInfoScreenData(String name, String lifeStage, String gender, String familyRole,
                                 String familyMembers, String activeBehavior, String behaviorStatus,
                                 String model, String profile, String health, String movementSpeed,
                                 String followRange, String position, String hunger, String energy, String safety,
                                 String morale, String intelligence, String anger, String education) {
        this(name, lifeStage, gender, familyRole, familyMembers, activeBehavior, behaviorStatus, model, profile,
                health, movementSpeed, followRange, position, hunger, energy, safety, morale, intelligence, anger,
                education, "UNEMPLOYED", "", "0", "", "NONE", "", "0", "", Optional.empty(), java.util.List.of(), Optional.empty());
    }

    public CitizenInfoScreenData(String name, String lifeStage, String gender, String model, String profile,
                                 String health, String movementSpeed, String followRange, String position,
                                 String hunger, String energy, String safety, String morale,
                                 String intelligence, String anger, String education) {
        this(name, lifeStage, gender, "NONE", "none", "idle", "", model, profile, health, movementSpeed, followRange, position,
                hunger, energy, safety, morale, intelligence, anger, education,
                "UNEMPLOYED", "", "0", "", "NONE", "", "0", "", Optional.empty(), java.util.List.of(), Optional.empty());
    }

    public CitizenInfoScreenData(String name, String lifeStage, String model, String profile,
                                 String health, String movementSpeed, String followRange, String position,
                                 String hunger, String energy, String safety, String morale,
                                 String intelligence, String anger, String education) {
        this(name, lifeStage, "UNKNOWN", "NONE", "none", "idle", "", model, profile, health, movementSpeed, followRange, position,
                hunger, energy, safety, morale, intelligence, anger, education,
                "UNEMPLOYED", "", "0", "", "NONE", "", "0", "", Optional.empty(), java.util.List.of(), Optional.empty());
    }
}
