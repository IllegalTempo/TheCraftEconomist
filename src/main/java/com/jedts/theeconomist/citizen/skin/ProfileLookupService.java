package com.jedts.theeconomist.citizen.skin;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class ProfileLookupService {
    private final ProfileResolver resolver;
    private final long positiveTtlNanos;
    private final long negativeTtlNanos;
    private final long timeoutMillis;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Optional<ResolvedProfile>>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore permits = new Semaphore(2);
    private final ScheduledExecutorService scheduler;

    public ProfileLookupService(ProfileResolver resolver) {
        this(resolver, Duration.ofHours(24), Duration.ofMinutes(10), Duration.ofSeconds(5), new ScheduledThreadPoolExecutor(1));
    }

    ProfileLookupService(ProfileResolver resolver, Duration positiveTtl, Duration negativeTtl, Duration timeout, ScheduledExecutorService scheduler) {
        this.resolver = resolver;
        this.positiveTtlNanos = positiveTtl.toNanos();
        this.negativeTtlNanos = negativeTtl.toNanos();
        this.timeoutMillis = timeout.toMillis();
        this.scheduler = scheduler;
    }

    static ProfileLookupService testing(ProfileResolver resolver) {
        return new ProfileLookupService(resolver, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofSeconds(1), new ScheduledThreadPoolExecutor(1));
    }

    public CompletionStage<Optional<ResolvedProfile>> lookup(String username) {
        String key = normalize(username);
        long now = System.nanoTime();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAtNanos > now) return CompletableFuture.completedFuture(cached.value);
        return inFlight.computeIfAbsent(key, ignored -> begin(key)).whenComplete((value, error) -> inFlight.remove(key));
    }

    private CompletableFuture<Optional<ResolvedProfile>> begin(String key) {
        if (!permits.tryAcquire()) return CompletableFuture.completedFuture(Optional.empty());
        CompletableFuture<Optional<ResolvedProfile>> result = new CompletableFuture<>();
        try {
            resolver.resolve(key).toCompletableFuture()
                    .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .whenComplete((value, error) -> {
                        permits.release();
                        Optional<ResolvedProfile> resolved = error == null && value != null ? value : Optional.empty();
                        cache.put(key, new CacheEntry(resolved, System.nanoTime() + (resolved.isPresent() ? positiveTtlNanos : negativeTtlNanos)));
                        result.complete(resolved);
                    });
        } catch (RuntimeException error) {
            permits.release();
            cache.put(key, new CacheEntry(Optional.empty(), System.nanoTime() + negativeTtlNanos));
            result.complete(Optional.empty());
        }
        return result;
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username must not be blank");
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private record CacheEntry(Optional<ResolvedProfile> value, long expiresAtNanos) {
    }
}
