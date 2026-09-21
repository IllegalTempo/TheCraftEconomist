package com.jedts.theeconomist.blueprint;

public record BlueprintTransitionResult(boolean accepted, BlueprintStackData data, String reason) {
    public static BlueprintTransitionResult accepted(BlueprintStackData data) {
        return new BlueprintTransitionResult(true, data, "");
    }

    public static BlueprintTransitionResult rejected(BlueprintStackData current, String reason) {
        return new BlueprintTransitionResult(false, current, reason);
    }
}
