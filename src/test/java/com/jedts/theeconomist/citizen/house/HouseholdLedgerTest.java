package com.jedts.theeconomist.citizen.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HouseholdLedgerTest {
    @Test
    void newHouseholdUsesConfiguredSurnameAndKeepsItOnRepeatedLookup() {
        HouseholdLedger ledger = new HouseholdLedger("minecraft:overworld");
        BlockPos anchor = new BlockPos(42, 70, -18);

        Household first = ledger.getOrCreate(anchor, List.of("River", "Stone"), new Random(7));
        Household repeated = ledger.getOrCreate(anchor, List.of("Changed"), new Random(99));

        assertTrue(first.residentCount() >= 2 && first.residentCount() <= 4);
        assertTrue(List.of("River", "Stone").contains(first.surname()));
        assertEquals(first.surname(), repeated.surname());
        assertEquals(first.residentCount(), repeated.residentCount());
    }

    @Test
    void issuingSameSlotTwiceOnlySucceedsOnceAndOtherAnchorsStayIndependent() {
        HouseholdLedger ledger = new HouseholdLedger("minecraft:overworld");
        Household first = ledger.getOrCreate(new BlockPos(1, 64, 1), List.of("River"), new Random(1));
        Household second = ledger.getOrCreate(new BlockPos(2, 64, 1), List.of("Stone"), new Random(2));

        assertTrue(first.issue(0));
        assertFalse(first.issue(0));
        assertTrue(second.issue(0));
        assertTrue(first.unissuedSlots().stream().noneMatch(slot -> slot == 0));
        assertNotEquals(first.slotId(0), second.slotId(0));
    }

    @Test
    void residentDisplayNamesKeepHouseholdOrderAndSurname() {
        HouseholdLedger ledger = new HouseholdLedger("minecraft:overworld");
        Household household = ledger.getOrCreate(new BlockPos(4, 64, 4), List.of("River"), new Random(1));
        household.rememberGivenName(1, "Tariq");
        household.rememberGivenName(0, "Amina");

        assertEquals(List.of("Amina River", "Tariq River"), household.residentDisplayNames());
    }
}
