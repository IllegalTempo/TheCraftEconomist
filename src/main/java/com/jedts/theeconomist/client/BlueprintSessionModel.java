package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintDraft;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;

import java.util.Objects;

public final class BlueprintSessionModel {
    private BlueprintSessionMode mode;
    private final BlueprintDraft draft;
    private final BlueprintDesign design;
    private int rotation;

    private BlueprintSessionModel(BlueprintSessionMode mode, BlueprintDraft draft, BlueprintDesign design) {
        this.mode = mode;
        this.draft = draft;
        this.design = design;
    }

    public static BlueprintSessionModel idle() {
        return new BlueprintSessionModel(BlueprintSessionMode.NONE, null, null);
    }

    public static BlueprintSessionModel designing() {
        return new BlueprintSessionModel(BlueprintSessionMode.DESIGN, new BlueprintDraft(), null);
    }

    public static BlueprintSessionModel placing(BlueprintDesign design) {
        return new BlueprintSessionModel(BlueprintSessionMode.PLACEMENT, null, Objects.requireNonNull(design));
    }

    public void rotate() {
        if (mode == BlueprintSessionMode.PLACEMENT) rotation = (rotation + 1) % 4;
    }

    public BlueprintSessionMode mode() {
        return mode;
    }

    public BlueprintDraft draft() {
        return draft;
    }

    public BlueprintDesign design() {
        return design;
    }

    public int rotation() {
        return rotation;
    }

    public boolean cancel() {
        if (mode == BlueprintSessionMode.NONE) return false;
        mode = BlueprintSessionMode.NONE;
        if (draft != null) draft.clear();
        return true;
    }

    public boolean shouldCancel(boolean playerAlive, boolean sameDimension,
                                boolean hasRelevantBlueprint, boolean disconnected) {
        return mode != BlueprintSessionMode.NONE
                && (!playerAlive || !sameDimension || !hasRelevantBlueprint || disconnected);
    }
}
