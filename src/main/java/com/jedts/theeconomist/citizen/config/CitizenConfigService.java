package com.jedts.theeconomist.citizen.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CitizenConfigService {
    private final Path path;
    private final AtomicReference<CitizenConfig> active;
    private volatile String startupMessage;

    private CitizenConfigService(Path path, CitizenConfig initial, String startupMessage) {
        this.path = path;
        this.active = new AtomicReference<>(initial);
        this.startupMessage = startupMessage;
    }

    public static CitizenConfigService load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            CitizenConfig defaults = CitizenConfig.defaults();
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, CitizenConfigCodec.encode(defaults), StandardCharsets.UTF_8);
                return new CitizenConfigService(path, defaults, "Citizen configuration created with built-in defaults");
            } catch (IOException exception) {
                return new CitizenConfigService(path, defaults, "Unable to write Citizen defaults: " + exception.getMessage());
            }
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            CitizenConfig loaded = CitizenConfigCodec.decode(json);
            String message = "Citizen configuration loaded";
            if (!CitizenConfigCodec.hasDecisionScoringSection(json)
                    || CitizenConfigCodec.hasLegacyLookAtPlayerAction(json)) {
                try {
                    Files.writeString(path, CitizenConfigCodec.encode(loaded), StandardCharsets.UTF_8);
                    message = "Citizen configuration loaded and upgraded to current decision scoring defaults";
                } catch (IOException exception) {
                    message = "Citizen configuration loaded; unable to write decision scoring defaults: " + exception.getMessage();
                }
            }
            return new CitizenConfigService(path, loaded, message);
        } catch (Exception exception) {
            return new CitizenConfigService(path, CitizenConfig.defaults(), "Invalid Citizen configuration: " + exception.getMessage());
        }
    }

    public CitizenConfig current() {
        return active.get();
    }

    public String startupMessage() {
        return startupMessage;
    }

    public ConfigReloadResult reload() {
        try {
            CitizenConfig parsed = CitizenConfigCodec.decode(Files.readString(path, StandardCharsets.UTF_8));
            active.set(parsed);
            startupMessage = "Citizen configuration loaded";
            return ConfigReloadResult.ok();
        } catch (Exception exception) {
            return ConfigReloadResult.failed("Invalid Citizen configuration: " + exception.getMessage());
        }
    }
}
