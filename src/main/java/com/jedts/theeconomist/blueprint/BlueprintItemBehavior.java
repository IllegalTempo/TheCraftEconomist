package com.jedts.theeconomist.blueprint;

public final class BlueprintItemBehavior {
    private BlueprintItemBehavior() { }

    public static BlueprintItemAction action(BlueprintState state) {
        return action(state, BlueprintSessionMode.NONE);
    }

    public static BlueprintItemAction action(BlueprintState state, BlueprintSessionMode mode) {
        if (mode == BlueprintSessionMode.DESIGN) return BlueprintItemAction.SAVE_DESIGN;
        if (mode == BlueprintSessionMode.PLACEMENT) return BlueprintItemAction.CONFIRM_PLACEMENT;
        return switch (state) {
            case EMPTY -> BlueprintItemAction.DESIGN;
            case DESIGNED -> BlueprintItemAction.PLACE;
            case PLANNED -> BlueprintItemAction.NONE;
        };
    }
}
