package com.jedts.theeconomist.citizen.config;

public record ConfigReloadResult(boolean success, String message) {
    public static ConfigReloadResult ok() {
        return new ConfigReloadResult(true, "Citizen configuration reloaded");
    }

    public static ConfigReloadResult failed(String message) {
        return new ConfigReloadResult(false, message);
    }
}
