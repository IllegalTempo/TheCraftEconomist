package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;
import com.jedts.theeconomist.blueprint.BlueprintStackData;

/** Client-only controller placeholder; rendering/input is added by the blueprint packet task. */
public final class BlueprintClientController {
    private static boolean designing;
    private static boolean placing;
    private static BlueprintDesign workingDesign;

    private BlueprintClientController() { }

    public static void startDesign() { designing = true; placing = false; }
    public static void startPlacement(ItemStack stack) {
        BlueprintStackData data = BlueprintStackData.read(stack);
        workingDesign = data.design();
        placing = workingDesign != null;
        designing = false;
    }
    public static void cancel() { designing = false; placing = false; }
    public static boolean designing() { return designing; }
    public static boolean placing() { return placing; }

    public static void confirmDesign(BlueprintDesign design) {
        workingDesign = design;
        ClientPlayNetworking.send(new BlueprintUpdatePayload(false, design, null));
    }

    public static void confirmPlacement(BlueprintPlacement placement) {
        if (workingDesign == null) return;
        ClientPlayNetworking.send(new BlueprintUpdatePayload(true, workingDesign, placement));
    }
}
