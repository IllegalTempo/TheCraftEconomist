package com.jedts.theeconomist.blueprint;

public final class BlueprintItemBehavior {
    private BlueprintItemBehavior() { }

    public static BlueprintItemAction action(BlueprintState state) {
        return switch (state) {
            case EMPTY -> BlueprintItemAction.DESIGN;
            case DESIGNED -> BlueprintItemAction.PLACE;
            case PLANNED -> BlueprintItemAction.NONE;
        };
    }
}
