package com.jedts.theeconomist.citizen.action;

import java.util.Objects;

public record CitizenItemActionResult(State state, String status) {
    public enum State { RUNNING, COMPLETE, UNAVAILABLE, FAILED }

    public CitizenItemActionResult {
        Objects.requireNonNull(state, "state");
        status = Objects.requireNonNullElse(status, "");
    }

    public static CitizenItemActionResult running(String status) { return new CitizenItemActionResult(State.RUNNING, status); }
    public static CitizenItemActionResult complete(String status) { return new CitizenItemActionResult(State.COMPLETE, status); }
    public static CitizenItemActionResult unavailable(String status) { return new CitizenItemActionResult(State.UNAVAILABLE, status); }
    public static CitizenItemActionResult failed(String status) { return new CitizenItemActionResult(State.FAILED, status); }
}
