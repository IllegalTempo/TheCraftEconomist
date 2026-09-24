package com.jedts.theeconomist.citizen.stats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CitizenStatRegistry {
    public static final CitizenStat HUNGER = CitizenStat.of("theeconomist:hunger", "Hunger", 100);
    public static final CitizenStat ENERGY = CitizenStat.of("theeconomist:energy", "Energy", 100);
    public static final CitizenStat SAFETY = CitizenStat.of("theeconomist:safety", "Safety", 50);
    public static final CitizenStat MORALE = CitizenStat.of("theeconomist:morale", "Morale", 50);
    public static final CitizenStat INTELLIGENCE = CitizenStat.of("theeconomist:intelligence", "Intelligence", 50);
    public static final CitizenStat ANGER = CitizenStat.of("theeconomist:anger", "Anger", 0);
    public static final CitizenStat EDUCATION = CitizenStat.of("theeconomist:education", "Education", 0);
    public static final CitizenStat AMBITION = CitizenStat.of("theeconomist:ambition", "Ambition", 50);
    public static final CitizenStat THRIFT = CitizenStat.of("theeconomist:thrift", "Thrift", 50);
    public static final CitizenStat BRAVERY = CitizenStat.of("theeconomist:bravery", "Bravery", 50);
    public static final CitizenStat SOCIABILITY = CitizenStat.of("theeconomist:sociability", "Sociability", 50);
    public static final CitizenStat LOYALTY = CitizenStat.of("theeconomist:loyalty", "Loyalty", 50);

    private static final List<CitizenStat> STATS = List.of(HUNGER, ENERGY, SAFETY, MORALE, INTELLIGENCE, ANGER,
            EDUCATION, AMBITION, THRIFT, BRAVERY, SOCIABILITY, LOYALTY);
    private static final Map<String, CitizenStat> BY_ID = index(STATS);

    private CitizenStatRegistry() {
    }

    public static List<CitizenStat> all() {
        return STATS;
    }

    public static boolean contains(CitizenStat stat) {
        return stat != null && BY_ID.get(stat.id()) == stat;
    }

    private static Map<String, CitizenStat> index(List<CitizenStat> stats) {
        Map<String, CitizenStat> result = new LinkedHashMap<>();
        for (CitizenStat stat : stats) {
            if (result.put(stat.id(), stat) != null) throw new IllegalArgumentException("Duplicate stat ID: " + stat.id());
        }
        return Collections.unmodifiableMap(result);
    }
}
