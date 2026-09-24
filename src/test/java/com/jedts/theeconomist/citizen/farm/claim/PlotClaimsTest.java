package com.jedts.theeconomist.citizen.farm.claim;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotClaimsTest {
    private static final PlotOwner ALICE = new PlotOwner(PlotOwner.Kind.PLAYER,
            UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final PlotOwner BOB = new PlotOwner(PlotOwner.Kind.PLAYER,
            UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(1, 64, 0);
    private static final BlockPos C = new BlockPos(2, 64, 0);

    @Test void adjacentDifferentOwnersRemainSeparatePlots() {
        PlotClaims claims = new PlotClaims();
        assertTrue(claims.claimNewFarmland(A, ALICE));
        assertTrue(claims.claimNewFarmland(B, BOB));
        assertEquals(Set.of(A), claims.component(A));
        assertEquals(Set.of(B), claims.component(B));
        assertEquals(BOB, claims.ownerAt(B).orElseThrow());
    }

    @Test void sameOwnerPlotsJoinAndSplitOnRemoval() {
        PlotClaims claims = new PlotClaims();
        claims.claimNewFarmland(A, ALICE);
        claims.claimNewFarmland(C, ALICE);
        assertEquals(Set.of(A), claims.component(A));
        claims.claimNewFarmland(B, ALICE);
        assertEquals(Set.of(A, B, C), claims.component(A));
        claims.remove(B);
        assertEquals(Set.of(A), claims.component(A));
        assertEquals(Set.of(C), claims.component(C));
    }

    @Test void diagonalFarmlandDoesNotJoin() {
        PlotClaims claims = new PlotClaims();
        BlockPos diagonal = new BlockPos(1, 64, 1);
        claims.claimNewFarmland(A, ALICE);
        claims.claimNewFarmland(diagonal, ALICE);
        assertEquals(Set.of(A), claims.component(A));
    }

    @Test void claimingUnownedComponentNeverStealsNeighbors() {
        PlotClaims claims = new PlotClaims();
        claims.claimNewFarmland(C, BOB);
        Set<BlockPos> farmland = Set.of(A, B, C);
        assertEquals(2, claims.claimUnclaimedComponent(A, ALICE, farmland::contains));
        assertEquals(ALICE, claims.ownerAt(B).orElseThrow());
        assertEquals(BOB, claims.ownerAt(C).orElseThrow());
    }

    @Test void oversizedComponentIsRejectedWithoutPartialClaim() {
        PlotClaims claims = new PlotClaims();
        assertEquals(0, claims.claimUnclaimedComponent(A, ALICE,
                pos -> pos.getY() == 64 && pos.getZ() == 0 && pos.getX() >= 0 && pos.getX() <= 4096));
        assertTrue(claims.ownerAt(A).isEmpty());
    }

    @Test void releasingCitizenClaimKeepsPlayerClaim() {
        PlotOwner citizen = new PlotOwner(PlotOwner.Kind.CITIZEN, UUID.randomUUID());
        PlotClaims claims = new PlotClaims();
        claims.claimNewFarmland(A, citizen);
        claims.claimNewFarmland(B, ALICE);
        assertEquals(1, claims.release(citizen));
        assertTrue(claims.ownerAt(A).isEmpty());
        assertEquals(ALICE, claims.ownerAt(B).orElseThrow());
    }
}
