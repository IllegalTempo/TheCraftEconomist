package com.jedts.theeconomist.citizen.farm.conflict;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChallengeCooldown {
    private final Map<Key, Long> allowedAt = new HashMap<>();

    public boolean tryStart(UUID owner, UUID challenger, BlockPos plotAnchor, long nowTick) {
        Key key = new Key(owner, challenger, plotAnchor.immutable());
        if (nowTick < allowedAt.getOrDefault(key, Long.MIN_VALUE)) return false;
        allowedAt.put(key, nowTick + 6_000);
        if (allowedAt.size() > 1024) allowedAt.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
        return true;
    }

    private record Key(UUID owner, UUID challenger, BlockPos plotAnchor) { }
}
