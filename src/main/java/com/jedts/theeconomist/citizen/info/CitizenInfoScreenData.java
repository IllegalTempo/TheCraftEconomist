package com.jedts.theeconomist.citizen.info;

public record CitizenInfoScreenData(String name, String lifeStage, String model, String profile,
                                    String health, String movementSpeed, String followRange, String position,
                                    String hunger, String energy, String safety, String morale,
                                    String intelligence, String anger, String education,
                                    String occupation, String employer, String wage, String workHours,
                                    String contractStatus, String contractTarget, String contractBounty,
                                    String contractDeadline) {
    public CitizenInfoScreenData(String name, String lifeStage, String model, String profile,
                                 String health, String movementSpeed, String followRange, String position,
                                 String hunger, String energy, String safety, String morale,
                                 String intelligence, String anger, String education) {
        this(name, lifeStage, model, profile, health, movementSpeed, followRange, position,
                hunger, energy, safety, morale, intelligence, anger, education,
                "UNEMPLOYED", "", "0", "", "NONE", "", "0", "");
    }
}
