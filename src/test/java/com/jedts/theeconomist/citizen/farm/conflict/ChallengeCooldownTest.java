package com.jedts.theeconomist.citizen.farm.conflict;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeCooldownTest {
    @Test void samePairAndPlotCannotRerollForQuarterDay() {
        ChallengeCooldown cooldown = new ChallengeCooldown();
        UUID owner = UUID.randomUUID();
        UUID challenger = UUID.randomUUID();
        BlockPos plot = new BlockPos(1, 64, 2);
        assertTrue(cooldown.tryStart(owner, challenger, plot, 100));
        assertFalse(cooldown.tryStart(owner, challenger, plot, 6_099));
        assertTrue(cooldown.tryStart(owner, challenger, plot, 6_100));
    }
}
