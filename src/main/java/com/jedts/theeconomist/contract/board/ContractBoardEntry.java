package com.jedts.theeconomist.contract.board;

import com.jedts.theeconomist.contract.ContractKind;

import java.util.Objects;
import java.util.UUID;

/** Immutable, client-safe projection of an open contract. */
public record ContractBoardEntry(UUID id, ContractKind kind, String target, int quantity,
                                 int bounty, int requiredSkill, long deadlineTick) {
    public ContractBoardEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(target);
        if (target.isBlank() || quantity <= 0 || bounty < 0 || requiredSkill < 0 || requiredSkill > 100 || deadlineTick < 0) {
            throw new IllegalArgumentException("invalid contract board entry");
        }
    }
}
