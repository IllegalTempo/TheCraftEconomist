package com.jedts.theeconomist.citizen.farm;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.jedts.theeconomist.citizen.farm.FarmerWorkPlanner.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class FarmerWorkPlannerTest {
    @Test void unloadedAndUnreachableTargetsAreIgnored() {
        FarmerWorkPlanner planner = new FarmerWorkPlanner();
        assertTrue(planner.choose(List.of(
                new FarmerWorkPlanner.WorkTarget(new BlockPos(0, 64, 0), HARVEST, false, true),
                new FarmerWorkPlanner.WorkTarget(new BlockPos(1, 64, 0), HOE, true, false))).isEmpty());
    }

    @Test void matureOwnedCropIsChosenBeforeHoeingNewGround() {
        FarmerWorkPlanner planner = new FarmerWorkPlanner();
        var hoe = new FarmerWorkPlanner.WorkTarget(new BlockPos(0, 64, 0), HOE, true, true);
        var harvest = new FarmerWorkPlanner.WorkTarget(new BlockPos(1, 64, 0), HARVEST, true, true);
        assertEquals(harvest, planner.choose(List.of(hoe, harvest)).orElseThrow());
    }

    @Test void hoeingExtendsOwnedFarmlandBeforeStartingAnIsolatedPatch() {
        FarmerWorkPlanner planner = new FarmerWorkPlanner();
        var isolated = new FarmerWorkPlanner.WorkTarget(new BlockPos(1, 64, 0), HOE, true, true);
        var adjacent = new FarmerWorkPlanner.WorkTarget(new BlockPos(5, 64, 4), HOE, true, true);
        Set<BlockPos> owned = Set.of(new BlockPos(5, 64, 5));
        assertEquals(adjacent, planner.choose(List.of(isolated, adjacent), new BlockPos(0, 64, 0), owned::contains)
                .orElseThrow());
    }

    @Test void equallyUsefulJobsPreferTheShorterWalk() {
        FarmerWorkPlanner planner = new FarmerWorkPlanner();
        var far = new FarmerWorkPlanner.WorkTarget(new BlockPos(8, 64, 0), PLANT, true, true);
        var near = new FarmerWorkPlanner.WorkTarget(new BlockPos(2, 64, 0), PLANT, true, true);
        assertEquals(near, planner.choose(List.of(far, near), new BlockPos(0, 64, 0), ignored -> false)
                .orElseThrow());
    }

    @Test void hydratedFarmlandIsChosenBeforeAdjacentDryGround() {
        FarmerWorkPlanner planner = new FarmerWorkPlanner();
        var adjacentDry = new FarmerWorkPlanner.WorkTarget(new BlockPos(1, 64, 0), HOE, true, true);
        var hydrated = new FarmerWorkPlanner.WorkTarget(new BlockPos(3, 64, 0), HOE, true, true);
        Set<BlockPos> owned = Set.of(new BlockPos(1, 64, 1));
        assertEquals(hydrated, planner.choose(List.of(adjacentDry, hydrated), new BlockPos(0, 64, 0),
                owned::contains, pos -> pos.equals(hydrated.pos())).orElseThrow());
    }
}
