package com.jedts.theeconomist.citizen.behavior.sleep;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CitizenBedSelectorTest {
    @Test
    void selectorUsesNearestDeterministicAvailableBed() {
        List<CitizenBedCandidate> beds = List.of(candidate(3, 64, 0), candidate(1, 64, 0), candidate(-1, 64, 0));

        assertEquals(new BlockPos(-1, 64, 0), CitizenBedSelector.select(new BlockPos(0, 64, 0), beds).orElseThrow());
    }

    @Test
    void selectorSkipsOccupiedAndUnreachableBeds() {
        List<CitizenBedCandidate> beds = List.of(
                new CitizenBedCandidate(new BlockPos(-1, 64, 0), true, true),
                new CitizenBedCandidate(new BlockPos(0, 64, 1), false, false),
                candidate(1, 64, 0), candidate(2, 64, 0));

        assertEquals(new BlockPos(1, 64, 0), CitizenBedSelector.select(new BlockPos(0, 64, 0), beds).orElseThrow());
    }

    private static CitizenBedCandidate candidate(int x, int y, int z) {
        return new CitizenBedCandidate(new BlockPos(x, y, z), false, true);
    }
}
