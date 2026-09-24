package com.jedts.theeconomist.client;

import java.util.HashSet;
import java.util.Set;

/** Per-screen expansion state for generic decision rows. */
final class DecisionTabState {
    private final Set<String> expandedActions = new HashSet<>();

    boolean toggle(String actionId) {
        if (expandedActions.remove(actionId)) return false;
        expandedActions.add(actionId);
        return true;
    }

    boolean isExpanded(String actionId) {
        return expandedActions.contains(actionId);
    }
}
