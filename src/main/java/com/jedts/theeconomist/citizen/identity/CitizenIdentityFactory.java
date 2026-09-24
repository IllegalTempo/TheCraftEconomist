package com.jedts.theeconomist.citizen.identity;

import com.jedts.theeconomist.citizen.config.CitizenConfig;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class CitizenIdentityFactory {
    private final RandomGenerator random;

    public CitizenIdentityFactory(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public CitizenIdentity create(java.util.UUID citizenId, CitizenConfig config) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(config, "config");
        String givenName = config.givenNames().get(random.nextInt(config.givenNames().size()));
        String familyName = config.familyNames().get(random.nextInt(config.familyNames().size()));
        Optional<String> profile = config.profileUsernames().isEmpty()
                ? Optional.empty()
                : Optional.of(config.profileUsernames().get(random.nextInt(config.profileUsernames().size())));
        return new CitizenIdentity(1, citizenId, givenName, familyName, CitizenLifeStage.ADULT,
                profile, CitizenAppearance.fallback(citizenId), random.nextBoolean() ? CitizenGender.MALE : CitizenGender.FEMALE);
    }

    public CitizenIdentity createForHouse(java.util.UUID citizenId, CitizenConfig config, String surname,
                                          Set<String> usedGivenNames) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(surname, "surname");
        Objects.requireNonNull(usedGivenNames, "usedGivenNames");
        var unusedNames = config.givenNames().stream().filter(name -> !usedGivenNames.contains(name)).toList();
        var givenNames = unusedNames.isEmpty() ? config.givenNames() : unusedNames;
        String givenName = givenNames.get(random.nextInt(givenNames.size()));
        Optional<String> profile = config.profileUsernames().isEmpty()
                ? Optional.empty()
                : Optional.of(config.profileUsernames().get(random.nextInt(config.profileUsernames().size())));
        return new CitizenIdentity(1, citizenId, givenName, surname, CitizenLifeStage.ADULT,
                profile, CitizenAppearance.fallback(citizenId), random.nextBoolean() ? CitizenGender.MALE : CitizenGender.FEMALE);
    }
}
