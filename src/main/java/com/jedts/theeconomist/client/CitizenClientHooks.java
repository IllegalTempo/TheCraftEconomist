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
                identity.lifeStage().name(), identity.gender().name(), identity.familyRole().name(),
                citizen.familyMembersSummary(), citizen.activeBehaviorId(), citizen.activeBehaviorStatus(),
                identity.appearance().modelType().name(),
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
        if (!CitizenInfoScreen.active(payload.entityId())) return;
        var overview = payload.overview();
        CitizenInfoScreenData data = new CitizenInfoScreenData(
                overview.name(), overview.lifeStage(), overview.gender(), overview.familyRole(), overview.familyMembers(),
                overview.activeBehavior(), overview.behaviorStatus(), overview.model(), overview.profile(), overview.health(),
                overview.movementSpeed(), overview.followRange(), overview.position(),
                Integer.toString(overview.hunger()), Integer.toString(overview.energy()),
                Integer.toString(overview.safety()), Integer.toString(overview.morale()),
                Integer.toString(overview.intelligence()), Integer.toString(overview.anger()),
                Integer.toString(overview.education()), payload.occupation(), payload.employer(), Integer.toString(payload.wage()),
                payload.workHours(), payload.contractStatus(), payload.contractTarget(), Integer.toString(payload.contractBounty()),
                Long.toString(payload.contractDeadline()), java.util.Optional.of(payload.details()), payload.decisions(),
                java.util.Optional.ofNullable(payload.craftingPreview()));
        CitizenInfoScreen.update(payload.entityId(), data);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static com.jedts.theeconomist.citizen.stats.CitizenStats identityStats(CitizenEntity citizen) {
        return citizen.stats();
    }
}
