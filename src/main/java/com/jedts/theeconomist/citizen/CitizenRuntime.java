package com.jedts.theeconomist.citizen;

import com.jedts.theeconomist.citizen.config.CitizenConfigService;
import com.jedts.theeconomist.citizen.config.ConfigReloadResult;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenIdentityFactory;
import com.jedts.theeconomist.contract.CitizenContractRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

public final class CitizenRuntime {
    private static CitizenConfigService config;
    private static CitizenIdentityFactory identityFactory;
    private static final CitizenContractRegistry CONTRACTS = new CitizenContractRegistry();

    private CitizenRuntime() {
    }

    public static synchronized void initialize() {
        if (config == null) {
            config = CitizenConfigService.load(Path.of("config", "theeconomist", "citizens.json"));
            identityFactory = new CitizenIdentityFactory(new Random());
        }
    }

    public static CitizenIdentity createIdentity(UUID citizenId) {
        initialize();
        return identityFactory.create(citizenId, config.current());
    }

    public static CitizenConfigService config() {
        initialize();
        return config;
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
