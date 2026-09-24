package com.jedts.theeconomist.citizen.action;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;

public interface CitizenItemAction {
    CitizenItemActionResult tick(CitizenBehaviorContext context);
    String status();
    default void stop(CitizenBehaviorContext context) { }
}
