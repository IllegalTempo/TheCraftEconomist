package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

public final class BlueprintValidator {
    private BlueprintValidator() { }

    public static BlueprintValidationResult validateDesign(BlueprintDesign design) {
        if (design == null) return BlueprintValidationResult.rejected("missing design");
        if (design.blocks().isEmpty()) return BlueprintValidationResult.rejected("design has no blocks");
        if (design.blocks().size() > BlueprintLimits.MAX_BLOCKS) return BlueprintValidationResult.rejected("too many blocks");
        for (BlueprintBlock block : design.blocks()) {
            String id = block.blockId();
            if (!id.startsWith("minecraft:") || id.contains("command_block") || id.contains("chest")
                    || id.contains("barrel") || id.contains("shulker") || id.contains("portal")
                    || id.contains("redstone") || id.contains("spawner")) {
                return BlueprintValidationResult.rejected("block is not in the approved building palette: " + id);
            }
        }
        return BlueprintValidationResult.accepted();
    }

    public static BlueprintValidationResult validatePlacement(BlueprintDesign design, BlueprintPlacement placement,
                                                               Predicate<BlockPos> protectedPosition) {
        BlueprintValidationResult designResult = validateDesign(design);
        if (!designResult.valid()) return designResult;
        if (placement == null) return BlueprintValidationResult.rejected("missing placement");
        for (BlueprintBlock block : design.blocks()) {
            if (protectedPosition.test(placement.worldPosition(design, block))) {
                return BlueprintValidationResult.rejected("placement contains a protected position");
            }
        }
        return BlueprintValidationResult.accepted();
    }
}
