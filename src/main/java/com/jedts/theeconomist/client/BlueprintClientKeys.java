package com.jedts.theeconomist.client;

import com.mojang.blaze3d.platform.InputConstants;

public final class BlueprintClientKeys {
    private static boolean rotateWasDown;

    private BlueprintClientKeys() {}

    public static void tick() {
        boolean down = InputConstants.isKeyDown(InputConstants.KEY_R);
        if (down && !rotateWasDown && BlueprintClientController.placing()) {
            BlueprintClientController.rotatePlacement();
        }
        rotateWasDown = down;
    }

    public static String rotateLabel() {
        return "R";
    }
}