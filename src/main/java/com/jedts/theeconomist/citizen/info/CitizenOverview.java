package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Locale;

/** Server values for the Overview page; Citizen identity and stats are not entity-synced. */
public record CitizenOverview(String name, String lifeStage, String gender, String familyRole, String familyMembers,
                              String activeBehavior, String behaviorStatus,
                              String model, String profile,
                              String health, String movementSpeed, String followRange, String position,
                              int hunger, int energy, int safety, int morale, int intelligence,
                              int anger, int education) {
    public CitizenOverview {
        if (name.length() > 128) name = name.substring(0, 128);
        if (profile.length() > 128) profile = profile.substring(0, 128);
        if (familyMembers.length() > 512) familyMembers = familyMembers.substring(0, 512);
        if (activeBehavior.length() > 32) activeBehavior = activeBehavior.substring(0, 32);
        if (behaviorStatus.length() > 128) behaviorStatus = behaviorStatus.substring(0, 128);
    }

    public CitizenOverview(String name, String lifeStage, String gender, String model, String profile,
                           String health, String movementSpeed, String followRange, String position,
                           int hunger, int energy, int safety, int morale, int intelligence,
                           int anger, int education) {
        this(name, lifeStage, gender, "NONE", "none", "idle", "", model, profile, health, movementSpeed, followRange,
                position, hunger, energy, safety, morale, intelligence, anger, education);
    }

    public CitizenOverview(String name, String lifeStage, String model, String profile,
                           String health, String movementSpeed, String followRange, String position,
                           int hunger, int energy, int safety, int morale, int intelligence,
                           int anger, int education) {
        this(name, lifeStage, "UNKNOWN", "NONE", "none", "idle", "", model, profile, health, movementSpeed, followRange, position,
                hunger, energy, safety, morale, intelligence, anger, education);
    }

    public static CitizenOverview from(CitizenEntity citizen) {
        var identity = citizen.identity();
        var stats = citizen.stats();
        return new CitizenOverview(identity.displayName(), identity.lifeStage().name(), identity.gender().name(),
                identity.familyRole().name(), citizen.familyMembersSummary(), citizen.activeBehaviorId(),
                citizen.activeBehaviorStatus(), identity.appearance().modelType().name(),
                identity.profileUsername().orElse("none"),
                format(citizen.getHealth()) + "/" + format(citizen.getMaxHealth()),
                format(citizen.getAttributeValue(Attributes.MOVEMENT_SPEED)),
                format(citizen.getAttributeValue(Attributes.FOLLOW_RANGE)),
                citizen.getBlockX() + ", " + citizen.getBlockY() + ", " + citizen.getBlockZ(),
                stats.hunger(), stats.energy(), stats.safety(), stats.morale(), stats.intelligence(),
                stats.anger(), stats.education());
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
