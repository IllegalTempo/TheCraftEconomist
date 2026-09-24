package com.jedts.theeconomist.blueprint;

/** Stable player-facing names for the blueprint lifecycle states. */
public final class BlueprintDisplayName {
    private BlueprintDisplayName() { }

    public static String forState(BlueprintState state) {
        return switch (state) {
            case EMPTY -> "Empty Blueprint";
            case DESIGNED -> "Designed Blueprint";
            case PLANNED -> "Planned Blueprint";
        };
    }
}
