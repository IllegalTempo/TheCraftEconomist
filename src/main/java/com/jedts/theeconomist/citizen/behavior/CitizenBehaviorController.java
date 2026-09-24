package com.jedts.theeconomist.citizen.behavior;

import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenBehaviorEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenScoreInputs;
import com.jedts.theeconomist.citizen.info.CitizenCraftingPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CitizenBehaviorController {
    private static final Logger LOGGER = LoggerFactory.getLogger("theeconomist/citizen-behavior");
    private final List<CitizenBehavior> behaviors;
    private CitizenBehavior active;
    private List<CitizenActionEvaluation> latestEvaluations = List.of();

    public CitizenBehaviorController(List<CitizenBehavior> behaviors) {
        Objects.requireNonNull(behaviors, "behaviors");
        var ids = new HashSet<String>();
        for (CitizenBehavior behavior : behaviors) {
            Objects.requireNonNull(behavior, "behavior");
            if (behavior.id() == null || behavior.id().isBlank())
                throw new IllegalArgumentException("behavior id must not be blank");
            if (!ids.add(behavior.id())) throw new IllegalArgumentException("duplicate behavior id: " + behavior.id());
        }
        this.behaviors = List.copyOf(behaviors);
    }

    public void tick(CitizenBehaviorContext context) {
        List<Candidate> evaluations = evaluateAll(context);
        Candidate best = highestEligible(evaluations);
        Candidate activeEvaluation = evaluations.stream().filter(candidate -> candidate.behavior() == active)
                .findFirst().orElse(null);

        if (active != null) {
            try {
                if (activeEvaluation == null || !activeEvaluation.evaluation().eligible()
                        || !active.canContinue(context)) {
                    stopActive(context, CitizenBehaviorStopReason.INELIGIBLE);
                    activeEvaluation = null;
                }
            } catch (RuntimeException exception) {
                failActive(context, exception);
                activeEvaluation = null;
            }
        }

        double switchingMargin = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring().switchingMargin();
        if (best != null && active != best.behavior()
                && shouldPreempt(activeEvaluation == null ? null : activeEvaluation.evaluation(), best.evaluation(), switchingMargin)) {
            if (active != null) stopActive(context, CitizenBehaviorStopReason.PREEMPTED);
            active = best.behavior();
            try {
                active.start(context);
            } catch (RuntimeException exception) {
                failActive(context, exception);
            }
        }

        if (active != null) {
            try {
                active.tick(context);
            } catch (RuntimeException exception) {
                failActive(context, exception);
            }
        }
        latestEvaluations = evaluations.stream()
                .map(candidate -> candidate.evaluation().withSelection(
                        candidate == best, candidate.behavior() == active))
                .toList();
    }

    public String activeId() {
        return active == null ? "none" : active.id();
    }

    public String activeStatus(CitizenBehaviorContext context) {
        return active == null ? "" : active.status(context);
    }

    public Optional<CitizenCraftingPreview> activeCraftingPreview() {
        return active == null ? Optional.empty() : active.craftingPreview();
    }

    public List<CitizenActionEvaluation> latestEvaluations() { return latestEvaluations; }

    public void stopAll(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) {
        if (active != null) stopActive(context, reason);
    }

    private List<Candidate> evaluateAll(CitizenBehaviorContext context) {
        List<Candidate> result = new ArrayList<>(behaviors.size());
        for (CitizenBehavior behavior : behaviors) {
            try {
                CitizenActionEvaluation evaluation = Objects.requireNonNull(behavior.evaluate(context), "behavior evaluation");
                if (!behavior.id().equals(evaluation.actionId()))
                    throw new IllegalStateException("behavior evaluation ID does not match registered ID");
                result.add(new Candidate(behavior, evaluation));
            } catch (RuntimeException exception) {
                LOGGER.error("Citizen behavior {} failed evaluation", behavior.id(), exception);
                result.add(new Candidate(behavior, CitizenBehaviorEvaluation.score(behavior.id(), behavior.id(), false,
                        new CitizenScoreInputs(0, 0, 0, 0, 0, 0), "Evaluation failed", false)));
            }
        }
        return result;
    }

    private Candidate highestEligible(List<Candidate> evaluations) {
        Candidate best = null;
        for (Candidate candidate : evaluations) {
            if (!candidate.evaluation().eligible()) continue;
            if (best == null || candidate.evaluation().emergencyOverride()
                    && !best.evaluation().emergencyOverride()
                    || candidate.evaluation().emergencyOverride() == best.evaluation().emergencyOverride()
                    && candidate.evaluation().score() > best.evaluation().score()) best = candidate;
        }
        return best;
    }

    private boolean shouldPreempt(CitizenActionEvaluation current, CitizenActionEvaluation candidate, double switchingMargin) {
        if (current == null) return true;
        if (candidate.emergencyOverride() != current.emergencyOverride()) return candidate.emergencyOverride();
        if (current.emergencyOverride()) return candidate.score() > current.score();
        return candidate.score() >= current.score() + switchingMargin;
    }

    private void stopActive(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) {
        CitizenBehavior stopping = active;
        active = null;
        try {
            stopping.stop(context, reason);
        } catch (RuntimeException exception) {
            LOGGER.error("Citizen behavior {} failed while stopping", stopping.id(), exception);
        } finally {
            context.navigation().stop();
            context.citizen().setSprinting(false);
        }
    }

    private void failActive(CitizenBehaviorContext context, RuntimeException exception) {
        String id = active == null ? "unknown" : active.id();
        LOGGER.error("Citizen behavior {} failed", id, exception);
        if (active != null) stopActive(context, CitizenBehaviorStopReason.ERROR);
    }

    private record Candidate(CitizenBehavior behavior, CitizenActionEvaluation evaluation) { }
}
