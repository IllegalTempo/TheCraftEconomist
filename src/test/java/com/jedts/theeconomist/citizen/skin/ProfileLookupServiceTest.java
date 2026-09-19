package com.jedts.theeconomist.citizen.skin;

import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ProfileLookupServiceTest {
    @Test
    void concurrentCaseVariantsShareOneResolverCallAndPositiveCache() {
        AtomicInteger calls = new AtomicInteger();
        ProfileResolver resolver = username -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.of(profile()));
        };
        ProfileLookupService service = ProfileLookupService.testing(resolver);

        Optional<ResolvedProfile> first = service.lookup(" Example ").toCompletableFuture().join();
        Optional<ResolvedProfile> second = service.lookup("example").toCompletableFuture().join();

        assertEquals(Optional.of(profile()), first);
        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void failedLookupIsNegativelyCached() {
        AtomicInteger calls = new AtomicInteger();
        ProfileLookupService service = ProfileLookupService.testing(username -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.empty());
        });

        assertEquals(Optional.empty(), service.lookup("missing").toCompletableFuture().join());
        assertEquals(Optional.empty(), service.lookup("MISSING").toCompletableFuture().join());
        assertEquals(1, calls.get());
    }

    private static ResolvedProfile profile() {
        return new ResolvedProfile(UUID.fromString("00000000-0000-0000-0000-000000000201"), "texture", "signature", CitizenModelType.WIDE);
    }
}
