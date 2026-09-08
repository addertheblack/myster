package com.myster.type.join;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

import com.myster.cid.ServerCid;

/** Bounds concurrent KDF work (intended for server side) and keeps a bounded, expiring per-caller failure backoff. */
public final class InvitationAttemptLimiter {
    private static final int MAX_FAILURE_KEYS = 1024;
    private static final long INITIAL_BACKOFF_MILLIS = 250;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    private final Semaphore verificationPermits;
    private final Clock clock;
    private final Map<Key, Failure> failures = new HashMap<>();

    public InvitationAttemptLimiter(int maximumConcurrentVerifications, Clock clock) {
        if (maximumConcurrentVerifications <= 0) {
            throw new IllegalArgumentException("Maximum concurrent verifications must be positive");
        }
        verificationPermits = new Semaphore(maximumConcurrentVerifications);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Returns false while this exact invitation/caller/address tuple is in backoff. */
    public synchronized boolean isAllowed(byte[] invitationId, ServerCid caller, String remoteAddress) {
        prune();
        Failure failure = failures.get(Key.of(invitationId, caller, remoteAddress));
        return failure == null || clock.millis() >= failure.retryAt();
    }

    /** Attempts to reserve one of the small number of permitted concurrent KDF operations. */
    public boolean tryAcquireVerification() {
        return verificationPermits.tryAcquire();
    }

    public void releaseVerification() {
        verificationPermits.release();
    }

    public synchronized void recordFailure(byte[] invitationId, ServerCid caller,
            String remoteAddress) {
        prune();
        Key key = Key.of(invitationId, caller, remoteAddress);
        Failure previous = failures.get(key);
        int count = previous == null ? 1 : Math.min(previous.count() + 1, 30);
        long multiplier = 1L << Math.min(count - 1, 16);
        long backoff = Math.min(MAX_BACKOFF_MILLIS, INITIAL_BACKOFF_MILLIS * multiplier);
        failures.put(key, new Failure(count, clock.millis() + backoff));
        if (failures.size() > MAX_FAILURE_KEYS) {
            failures.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().retryAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(failures::remove);
        }
    }

    public synchronized void recordSuccess(byte[] invitationId, ServerCid caller,
            String remoteAddress) {
        failures.remove(Key.of(invitationId, caller, remoteAddress));
    }

    private void prune() {
        long now = clock.millis();
        failures.entrySet().removeIf(entry -> now >= entry.getValue().retryAt()
                + MAX_BACKOFF_MILLIS);
    }

    private record Key(String invitationHex, ServerCid caller, String remoteAddress) {
        static Key of(byte[] invitationId, ServerCid caller, String remoteAddress) {
            Objects.requireNonNull(invitationId, "invitationId");
            return new Key(HexFormat.of().formatHex(invitationId),
                    Objects.requireNonNull(caller, "caller"),
                    Objects.requireNonNull(remoteAddress, "remoteAddress"));
        }
    }

    private record Failure(int count, long retryAt) {}
}
