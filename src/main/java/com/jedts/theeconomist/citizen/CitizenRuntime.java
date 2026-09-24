package com.jedts.theeconomist.citizen;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorRegistry;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenGender;
import com.jedts.theeconomist.citizen.identity.CitizenIdentityFactory;
import com.jedts.theeconomist.citizen.skin.MojangProfileResolver;
import com.jedts.theeconomist.citizen.skin.ProfileLookupService;
import com.jedts.theeconomist.citizen.config.CitizenConfigService;
import com.jedts.theeconomist.citizen.config.ConfigReloadResult;
import com.jedts.theeconomist.citizen.config.DecisionScoringConfig;
import com.jedts.theeconomist.contract.CitizenContractRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

public final class CitizenRuntime {
    private static CitizenConfigService config;
    private static CitizenIdentityFactory identityFactory;
    private static ProfileLookupService profileLookup;
    private static CitizenBehaviorRegistry behaviorRegistry;
    private static final CitizenContractRegistry CONTRACTS = new CitizenContractRegistry();

    private CitizenRuntime() {
    }

    public static synchronized void initialize() {
        if (config == null) {
            config = CitizenConfigService.load(Path.of("config", "theeconomist", "citizens.json"));
            identityFactory = new CitizenIdentityFactory(new Random());
            profileLookup = new ProfileLookupService(new MojangProfileResolver());
        }
    }

    public static synchronized void initialize(CitizenBehaviorRegistry behaviorRegistry) {
        Objects.requireNonNull(behaviorRegistry, "behaviorRegistry");
        initialize();
        if (CitizenRuntime.behaviorRegistry != null
                && CitizenRuntime.behaviorRegistry != behaviorRegistry) {
            throw new IllegalStateException("citizen behavior registry is already initialized");
        }
        CitizenRuntime.behaviorRegistry = behaviorRegistry;
    }

    public static synchronized CitizenBehaviorRegistry behaviorRegistry() {
        if (behaviorRegistry == null) {
            throw new IllegalStateException("citizen behavior registry has not been initialized by CitizenModule");
        }
        return behaviorRegistry;
    }

    public static CitizenIdentity createIdentity(UUID citizenId) {
        initialize();
        return identityFactory.create(citizenId, config.current());
    }

    public static CitizenIdentity createHouseIdentity(UUID citizenId, String surname, Set<String> usedGivenNames) {
        initialize();
        return identityFactory.createForHouse(citizenId, config.current(), surname, usedGivenNames);
    }

    public static CitizenGender genderFor(UUID citizenId) {
        return CitizenGender.forFallback(citizenId);
    }

    public static CitizenConfigService config() {
        initialize();
        return config;
    }

    public static DecisionScoringConfig decisionScoring() {
        return config().current().decisionScoring();
    }

    public static ProfileLookupService profileLookup() {
        initialize();
        return profileLookup;
    }

    public static CitizenContractRegistry contracts() {
        return CONTRACTS;
    }

    public static int reload(CommandSourceStack source) {
        ConfigReloadResult result = config().reload();
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), true);
            return 1;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }
}
