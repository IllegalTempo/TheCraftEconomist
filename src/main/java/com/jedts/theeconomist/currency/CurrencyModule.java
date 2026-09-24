package com.jedts.theeconomist.currency;

import com.jedts.theeconomist.api.module.TheEconomistModule;

public final class CurrencyModule implements TheEconomistModule {
    @Override
    public String id() {
        return "currency";
    }

    @Override
    public void initialize() {
        CrownItems.register();
    }
}
