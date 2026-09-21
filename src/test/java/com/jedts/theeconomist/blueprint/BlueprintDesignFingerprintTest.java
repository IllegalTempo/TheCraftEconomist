package com.jedts.theeconomist.blueprint;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlueprintDesignFingerprintTest {
    @Test
    void changing_chest_contents_changes_design_fingerprint() {
        CompoundTag firstContents = new CompoundTag();
        firstContents.putString("id", "minecraft:chest");
        firstContents.putString("Items", "diamonds");
        CompoundTag secondContents = firstContents.copy();
        secondContents.putString("Items", "dirt");
        BlueprintDesign first = new BlueprintDesign(1, 1, 1, List.of(
                new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north", firstContents)));
        BlueprintDesign second = new BlueprintDesign(1, 1, 1, List.of(
                new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north", secondContents)));

        assertNotEquals(BlueprintDesignFingerprint.of(first), BlueprintDesignFingerprint.of(second));
    }
}
