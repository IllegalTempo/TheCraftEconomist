package com.jedts.theeconomist.api.module;

/**
 * A self-contained feature that participates in The Economist's startup.
 *
 * <p>Future features should keep their registrations inside one module and
 * expose only the dependencies other modules actually need.</p>
 */
public interface TheEconomistModule {
    /**
     * Returns the stable, human-readable ID used to detect configuration mistakes.
     */
    String id();

    /**
     * Registers this module's content and event handlers.
     */
    void initialize();
}
