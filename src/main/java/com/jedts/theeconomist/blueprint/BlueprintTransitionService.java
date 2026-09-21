package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

public final class BlueprintTransitionService {
    private BlueprintTransitionService() { }

    public static BlueprintTransitionResult saveDesign(BlueprintStackData current, BlueprintDesign proposed) {
        if (current.state() != BlueprintState.EMPTY) {
            return BlueprintTransitionResult.rejected(current, "blueprint is not empty");
        }
        BlueprintValidationResult validation = BlueprintValidator.validateDesign(proposed);
        if (!validation.valid()) return BlueprintTransitionResult.rejected(current, validation.reason());
        return BlueprintTransitionResult.accepted(new BlueprintStackData(BlueprintState.DESIGNED, proposed, null));
    }

    public static BlueprintTransitionResult confirmPlacement(BlueprintStackData current, BlueprintPlacement proposed,
                                                               String currentDimension, Predicate<BlockPos> occupied) {
        return confirmPlacement(current, proposed, currentDimension, occupied,
                current.design() == null ? 0 : current.design().hashCode());
    }

    public static BlueprintTransitionResult confirmPlacement(BlueprintStackData current, BlueprintPlacement proposed,
                                                               String currentDimension, Predicate<BlockPos> occupied,
                                                               int expectedDesignHash) {
        if (current.state() != BlueprintState.DESIGNED || current.design() == null) {
            return BlueprintTransitionResult.rejected(current, "blueprint is not designed");
        }
        if (current.design().hashCode() != expectedDesignHash) {
            return BlueprintTransitionResult.rejected(current, "blueprint changed since preview started");
        }
        if (proposed == null || !proposed.dimension().equals(currentDimension)) {
            return BlueprintTransitionResult.rejected(current, "placement must be in the current dimension");
        }
        BlueprintValidationResult validation = BlueprintValidator.validatePlacement(current.design(), proposed, occupied);
        if (!validation.valid()) return BlueprintTransitionResult.rejected(current, validation.reason());
        return BlueprintTransitionResult.accepted(
                new BlueprintStackData(BlueprintState.PLANNED, current.design(), proposed));
    }
}
