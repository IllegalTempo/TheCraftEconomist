package com.jedts.theeconomist.blueprint;

public record BlueprintValidationResult(boolean valid, String reason) {
    public static BlueprintValidationResult accepted() { return new BlueprintValidationResult(true, ""); }
    public static BlueprintValidationResult rejected(String reason) { return new BlueprintValidationResult(false, reason); }
}
