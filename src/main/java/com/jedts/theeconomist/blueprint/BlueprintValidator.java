package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.function.Predicate;

public final class BlueprintValidator {
    private BlueprintValidator() { }

    public static BlueprintValidationResult validateDesign(BlueprintDesign design) {
        if (design == null) return BlueprintValidationResult.rejected("missing design");
        if (design.blocks().isEmpty()) return BlueprintValidationResult.rejected("design has no blocks");
        if (design.blocks().size() > BlueprintLimits.MAX_BLOCKS) return BlueprintValidationResult.rejected("too many blocks");
        for (BlueprintBlock block : design.blocks()) {
            BlueprintValidationResult blockResult = validateBlock(block);
            if (!blockResult.valid()) return blockResult;
            String id = block.blockId();
            if (id.contains("command_block") || id.contains("chest")
                    || id.contains("barrel") || id.contains("shulker") || id.contains("portal")
                    || id.contains("redstone") || id.contains("spawner")) {
                return BlueprintValidationResult.rejected("block is not in the approved building palette: " + id);
            }
        }
        return BlueprintValidationResult.accepted();
    }

    private static BlueprintValidationResult validateBlock(BlueprintBlock block) {
        Identifier id = Identifier.tryParse(block.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return BlueprintValidationResult.rejected("unknown block id: " + block.blockId());
        }
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for (String encoded : block.stateProperties().split(",")) {
            if (encoded.isBlank()) continue;
            String[] pair = encoded.split("=", 2);
            Property<?> property = pair.length == 2
                    ? state.getBlock().getStateDefinition().getProperty(pair[0]) : null;
            if (property == null || property.getValue(pair[1]).isEmpty()) {
                return BlueprintValidationResult.rejected("invalid block state property: " + encoded);
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
