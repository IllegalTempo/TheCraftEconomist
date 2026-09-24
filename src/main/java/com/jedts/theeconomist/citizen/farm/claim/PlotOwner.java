package com.jedts.theeconomist.citizen.farm.claim;

import java.util.Objects;
import java.util.UUID;

public record PlotOwner(Kind kind, UUID id) {
    public enum Kind { PLAYER, CITIZEN, HOUSEHOLD }

    public PlotOwner {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
