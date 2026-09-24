package com.jedts.theeconomist.citizen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CitizenModuleTest {
    @Test
    void defaultBehaviorContributionsKeepTheirExistingOrder() {
        assertEquals(List.of("escape_water", "panic", "avoid_monster", "combat", "sleep", "confused",
                        "find_seeds", "composter", "farmer", "contract", "return_home",
                        "look_at_creature", "wander", "idle"),
                CitizenModule.createBehaviorRegistry().behaviorIds());
    }
}
