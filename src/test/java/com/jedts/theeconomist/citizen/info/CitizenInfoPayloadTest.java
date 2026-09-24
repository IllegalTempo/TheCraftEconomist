package com.jedts.theeconomist.citizen.info;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class CitizenInfoPayloadTest {
    @Test
    void acceptsThirteenDecisionCandidates() {
        var decisions = java.util.stream.IntStream.range(0, 13)
                .mapToObj(index -> new CitizenDecisionView("action" + index, "Action " + index,
                        false, 0, false, "unavailable", List.of(), false, false)).toList();
        var overview = new CitizenOverview("Amina", "ADULT", "FEMALE", "WIDE", "none",
                "20/20", "0.3", "24", "0, 64, 0", 100, 100, 100, 50, 50, 0, 0);
        var details = new CitizenInfoDetails(java.util.Collections.nCopies(27,
                net.minecraft.world.item.ItemStack.EMPTY), "idle", "none", 0, 0, 0, 1, 1, 1);
        assertDoesNotThrow(() -> new CitizenInfoPayload(1, "NONE", "", 0, "",
                "NONE", "", 0, 0, overview, details, decisions));
    }
}
