package com.jedts.theeconomist.core.module;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import com.jedts.theeconomist.currency.CurrencyModule;
import com.jedts.theeconomist.citizen.CitizenModule;
import com.jedts.theeconomist.blueprint.BlueprintModule;
import com.jedts.theeconomist.trade.TradeModule;

import java.util.List;

/**
 * The ordered inventory of enabled feature modules.
 *
 * <p>Add future modules here. Declared dependencies determine initialization
 * order; this declaration order breaks ties between modules that are ready
 * together. See {@code docs/architecture/adding-a-feature.md} for the addition
 * workflow.</p>
 */
public final class ModModules {
    private static final List<TheEconomistModule> MODULES = List.of(
            new CurrencyModule(),
            new CitizenModule(),
            new BlueprintModule(),
            new TradeModule()
    );

    private ModModules() {
    }

    public static List<TheEconomistModule> all() {
        return MODULES;
    }
}
