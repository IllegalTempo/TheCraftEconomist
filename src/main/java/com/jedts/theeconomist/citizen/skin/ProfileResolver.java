package com.jedts.theeconomist.citizen.skin;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ProfileResolver {
    CompletionStage<Optional<ResolvedProfile>> resolve(String username);
}
