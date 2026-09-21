package com.jedts.theeconomist.client;

import com.jedts.theeconomist.TheEconomistMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class BlueprintClientKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "blueprint"));
    public static final KeyMapping ROTATE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.theeconomist.rotate_blueprint", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));

    private BlueprintClientKeys() { }

    public static void tick() {
        while (ROTATE.consumeClick()) {
            if (BlueprintClientController.placing()) BlueprintClientController.rotatePlacement();
        }
    }
}
