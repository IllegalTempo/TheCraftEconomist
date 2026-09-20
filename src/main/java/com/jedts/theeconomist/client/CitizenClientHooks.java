package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoScreenData;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class CitizenClientHooks {
    private CitizenClientHooks() {
    }

    public static void open(Object entity) {
        CitizenEntity citizen = (CitizenEntity) entity;
        var identity = citizen.identity();
        CitizenInfoScreenData data = new CitizenInfoScreenData(
                citizen.getCustomName() == null ? identity.displayName() : citizen.getCustomName().getString(),
                identity.lifeStage().name(), identity.appearance().modelType().name(),
                identity.profileUsername().orElse("none"),
                format(citizen.getHealth()) + "/" + format(citizen.getMaxHealth()),
                format(citizen.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)),
                format(citizen.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                citizen.getBlockX() + ", " + citizen.getBlockY() + ", " + citizen.getBlockZ(),
                Integer.toString(identityStats(citizen).hunger()), Integer.toString(identityStats(citizen).energy()),
                Integer.toString(identityStats(citizen).safety()), Integer.toString(identityStats(citizen).morale()),
                Integer.toString(identityStats(citizen).intelligence()), Integer.toString(identityStats(citizen).anger()),
                Integer.toString(identityStats(citizen).education()));
        Minecraft.getInstance().setScreenAndShow(new CitizenInfoScreen(citizen, data));
    }

    public static void open(CitizenEntity citizen, CitizenInfoPayload payload) {
        var identity = citizen.identity();
        var stats = citizen.stats();
        CitizenInfoScreenData data = new CitizenInfoScreenData(
                citizen.getCustomName() == null ? identity.displayName() : citizen.getCustomName().getString(),
                identity.lifeStage().name(), identity.appearance().modelType().name(), identity.profileUsername().orElse("none"),
                format(citizen.getHealth()) + "/" + format(citizen.getMaxHealth()),
                format(citizen.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)),
                format(citizen.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)),
                citizen.getBlockX() + ", " + citizen.getBlockY() + ", " + citizen.getBlockZ(),
                Integer.toString(stats.hunger()), Integer.toString(stats.energy()), Integer.toString(stats.safety()),
                Integer.toString(stats.morale()), Integer.toString(stats.intelligence()), Integer.toString(stats.anger()),
                Integer.toString(stats.education()), payload.occupation(), payload.employer(), Integer.toString(payload.wage()),
                payload.workHours(), payload.contractStatus(), payload.contractTarget(), Integer.toString(payload.contractBounty()),
                Long.toString(payload.contractDeadline()));
        Minecraft.getInstance().setScreenAndShow(new CitizenInfoScreen(citizen, data));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static com.jedts.theeconomist.citizen.stats.CitizenStats identityStats(CitizenEntity citizen) {
        return citizen.stats();
    }
}
