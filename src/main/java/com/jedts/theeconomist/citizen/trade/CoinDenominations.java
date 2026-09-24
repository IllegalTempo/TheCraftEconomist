package com.jedts.theeconomist.citizen.trade;

import com.jedts.theeconomist.currency.CrownDenomination;
import com.jedts.theeconomist.currency.CrownItems;
import net.minecraft.world.item.Item;

import java.util.Objects;

public record CoinDenominations(Item copper, Item silver, Item gold) {
    public CoinDenominations {
        Objects.requireNonNull(copper);
        Objects.requireNonNull(silver);
        Objects.requireNonNull(gold);
    }

    public static CoinDenominations registered() {
        return new CoinDenominations(CrownItems.item(CrownDenomination.COPPER),
                CrownItems.item(CrownDenomination.SILVER), CrownItems.item(CrownDenomination.GOLD));
    }
}
