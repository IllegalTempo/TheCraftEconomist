package com.jedts.theeconomist.citizen.behavior;

import com.jedts.theeconomist.citizen.behavior.decision.CitizenBehaviorEvaluation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenBehaviorRegistryTest {
    @Test
    void builderPreservesRegistrationOrderAndCreatesFreshBehaviorInstances() {
        AtomicInteger creations = new AtomicInteger();
        CitizenBehaviorRegistry registry = CitizenBehaviorRegistry.builder()
                .register("first", () -> { creations.incrementAndGet(); return behavior("first"); })
                .register("second", () -> { creations.incrementAndGet(); return behavior("second"); })
                .build();

        var firstController = registry.createController();
        var secondController = registry.createController();

        assertEquals(4, creations.get());
        assertEquals(java.util.List.of("first", "second"), registry.behaviorIds());
        assertTrue(firstController != secondController);
    }

    @Test
    void buildingRegistryDoesNotInstantiateBehaviorFactories() {
        AtomicInteger creations = new AtomicInteger();

        CitizenBehaviorRegistry.builder()
                .register("first", () -> { creations.incrementAndGet(); return behavior("first"); })
                .build();

        assertEquals(0, creations.get());
    }

    @Test
    void builderRejectsDuplicateIds() {
        CitizenBehaviorRegistry.Builder builder = CitizenBehaviorRegistry.builder();
        builder.register("duplicate", () -> behavior("duplicate"));

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("duplicate", () -> behavior("duplicate")));
    }

    @Test
    void builderRejectsBehaviorWhoseIdDoesNotMatchRegistration() {
        CitizenBehaviorRegistry registry = CitizenBehaviorRegistry.builder()
                .register("registered", () -> behavior("different"))
                .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                registry::createController);

        assertTrue(error.getMessage().contains("registered"));
        assertTrue(error.getMessage().contains("different"));
    }

    @Test
    void builderRejectsNullFactories() {
        assertThrows(NullPointerException.class,
                () -> CitizenBehaviorRegistry.builder().register("null", null));
    }

    @Test
    void builderReportsFactoryExceptionsWithBehaviorId() {
        CitizenBehaviorRegistry registry = CitizenBehaviorRegistry.builder()
                .register("broken", () -> { throw new IllegalStateException("boom"); })
                .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                registry::createController);

        assertTrue(error.getMessage().contains("broken"));
    }

    private static CitizenBehavior behavior(String id) {
        return new CitizenBehavior() {
            @Override public String id() { return id; }
            @Override public boolean canStart(CitizenBehaviorContext context) { return false; }
            @Override public com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation evaluate(
                    CitizenBehaviorContext context) {
                return CitizenBehaviorEvaluation.unavailable(id, id, "test behavior");
            }
            @Override public void tick(CitizenBehaviorContext context) { }
            @Override public String status(CitizenBehaviorContext context) { return ""; }
        };
    }
}
