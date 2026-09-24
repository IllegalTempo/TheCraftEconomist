package com.jedts.theeconomist.citizen.behavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Immutable, ordered set of behavior factories used to create citizen controllers. */
public final class CitizenBehaviorRegistry {
    private final List<Entry> entries;
    private final List<String> behaviorIds;

    private CitizenBehaviorRegistry(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.behaviorIds = this.entries.stream().map(Entry::id).toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public CitizenBehaviorController createController() {
        List<CitizenBehavior> behaviors = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            CitizenBehavior behavior;
            try {
                behavior = Objects.requireNonNull(entry.factory().get(), "behavior factory result");
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Could not create citizen behavior '" + entry.id() + "'", exception);
            }
            String actualId = behavior.id();
            if (!entry.id().equals(actualId)) {
                throw new IllegalArgumentException("Registered behavior ID '" + entry.id()
                        + "' does not match factory behavior ID '" + actualId + "'");
            }
            behaviors.add(behavior);
        }
        return new CitizenBehaviorController(behaviors);
    }

    public List<String> behaviorIds() {
        return behaviorIds;
    }

    private record Entry(String id, Supplier<CitizenBehavior> factory) { }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();
        private boolean built;

        private Builder() {
        }

        public Builder register(String id, Supplier<CitizenBehavior> factory) {
            if (built) throw new IllegalStateException("citizen behavior registry is already built");
            if (id == null || id.isBlank()) throw new IllegalArgumentException("behavior ID must not be blank");
            Objects.requireNonNull(factory, "behavior factory");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate behavior id: " + id);
            entries.add(new Entry(id, factory));
            return this;
        }

        public CitizenBehaviorRegistry build() {
            if (built) throw new IllegalStateException("citizen behavior registry is already built");
            built = true;
            return new CitizenBehaviorRegistry(entries);
        }
    }
}
