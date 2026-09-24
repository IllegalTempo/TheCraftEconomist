package com.jedts.theeconomist.citizen.behavior;

import org.junit.jupiter.api.Test;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenBehaviorControllerTest {
    private static final CitizenBehaviorContext CONTEXT = null;

    @Test
    void higherPriorityBehaviorPreemptsAndStopsCurrentFirst() {
        List<String> events = new ArrayList<>();
        TestBehavior low = new TestBehavior("low", 30, events);
        TestBehavior high = new TestBehavior("high", 40, events);
        high.eligible = false;
        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(low, high));

        controller.tick(CONTEXT);
        high.eligible = true;
        controller.tick(CONTEXT);

        assertEquals(List.of("start low", "tick low", "stop low PREEMPTED", "start high", "tick high"), events);
        assertEquals("high", controller.activeId());
    }

    @Test
    void equalPriorityKeepsRegistrationOrderWithoutThrashing() {
        List<String> events = new ArrayList<>();
        TestBehavior first = new TestBehavior("first", 40, events);
        TestBehavior second = new TestBehavior("second", 40, events);
        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(first, second));

        controller.tick(CONTEXT);
        controller.tick(CONTEXT);

        assertEquals(List.of("start first", "tick first", "tick first"), events);
    }

    @Test
    void highestUtilityWinsAndIneligibleBehaviorsAreIgnored() {
        List<String> events = new ArrayList<>();
        TestBehavior lower = new TestBehavior("lower", 75, events);
        TestBehavior higher = new TestBehavior("higher", 80, events);
        TestBehavior unavailable = new TestBehavior("unavailable", 99, events);
        unavailable.eligible = false;

        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(lower, higher, unavailable));
        controller.tick(CONTEXT);

        assertEquals("higher", controller.activeId());
        assertEquals(List.of("start higher", "tick higher"), events);
    }

    @Test
    void keepsCurrentBehaviorUnlessChallengerClearsSwitchingMargin() {
        List<String> events = new ArrayList<>();
        TestBehavior current = new TestBehavior("current", 70, events);
        TestBehavior challenger = new TestBehavior("challenger", 74, events);
        challenger.eligible = false;
        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(current, challenger));

        controller.tick(CONTEXT);
        challenger.eligible = true;
        controller.tick(CONTEXT);
        assertTrue(controller.latestEvaluations().stream().anyMatch(e -> e.actionId().equals("challenger") && e.selected()));
        assertTrue(controller.latestEvaluations().stream().anyMatch(e -> e.actionId().equals("current") && e.active()));
        challenger.score = 76;
        controller.tick(CONTEXT);

        assertEquals("challenger", controller.activeId());
        assertEquals(List.of("start current", "tick current", "tick current", "stop current PREEMPTED",
                "start challenger", "tick challenger"), events);
    }

    @Test
    void emergencyOverridePreemptsRegardlessOfUtilityScore() {
        List<String> events = new ArrayList<>();
        TestBehavior routine = new TestBehavior("routine", 100, events);
        TestBehavior emergency = new TestBehavior("emergency", 1, events);
        emergency.override = true;
        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(routine, emergency));

        controller.tick(CONTEXT);

        assertEquals("emergency", controller.activeId());
        assertEquals(List.of("start emergency", "tick emergency"), events);
    }

    @Test
    void behaviorExceptionCleansUpAndAllowsRecoveryNextTick() {
        List<String> events = new ArrayList<>();
        TestBehavior broken = new TestBehavior("broken", 20, events);
        broken.throwOnTick = true;
        TestBehavior fallback = new TestBehavior("fallback", 10, events);
        CitizenBehaviorController controller = new CitizenBehaviorController(List.of(broken, fallback));

        controller.tick(CONTEXT);
        broken.eligible = false;
        controller.tick(CONTEXT);

        assertEquals(CitizenBehaviorStopReason.ERROR, broken.lastStopReason);
        assertEquals("fallback", controller.activeId());
        assertEquals("working", controller.activeStatus(CONTEXT));
    }

    private static final class TestBehavior implements CitizenBehavior {
        private final String id;
        private final List<String> events;
        private double score;
        private boolean eligible = true;
        private boolean throwOnTick;
        private boolean override;
        private CitizenBehaviorStopReason lastStopReason;

        private TestBehavior(String id, double score, List<String> events) {
            this.id = id;
            this.score = score;
            this.events = events;
        }

        @Override public String id() { return id; }
        @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
            return new CitizenActionEvaluation(id, id, eligible, score, override, "test decision", List.of(), false, false);
        }
        @Override public boolean canStart(CitizenBehaviorContext context) { return eligible; }
        @Override public boolean canContinue(CitizenBehaviorContext context) { return eligible; }
        @Override public void start(CitizenBehaviorContext context) { events.add("start " + id); }
        @Override public void tick(CitizenBehaviorContext context) {
            events.add("tick " + id);
            if (throwOnTick) throw new IllegalStateException("boom");
        }
        @Override public void stop(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) {
            lastStopReason = reason;
            events.add("stop " + id + " " + reason);
        }
        @Override public String status(CitizenBehaviorContext context) { return "working"; }
    }
}
