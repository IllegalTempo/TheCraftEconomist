package com.jedts.theeconomist.api.module;

import java.util.Set;

/**
 * A self-contained feature that participates in The Economist's startup.
 *
 * <p>Future features should keep their registrations inside one module and
 * expose only the dependencies other modules actually need. See
 * {@code docs/architecture/adding-a-feature.md} for the contributor workflow.</p>
 */
public interface TheEconomistModule {
    /**
     * Returns the stable, human-readable ID used to detect configuration mistakes.
     */
    String id();

    /**
     * Returns IDs of modules that must initialize before this module.
     */
    default Set<String> dependencies() {
        return Set.of();
    }

    /**
     * Registers this module's content and event handlers.
     */
    void initialize();
}
