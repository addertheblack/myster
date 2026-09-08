package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;

class TestInvitationAttemptLimiter {
    @Test
    void boundsConcurrencyAndBacksOffOnlyExactTuple() {
        MutableClock clock = new MutableClock();
        InvitationAttemptLimiter limiter = new InvitationAttemptLimiter(1, clock);
        byte[] id = new byte[16];
        ServerCid caller = new ServerCid(new byte[16]);

        assertTrue(limiter.tryAcquireVerification());
        assertFalse(limiter.tryAcquireVerification());
        limiter.releaseVerification();
        assertTrue(limiter.tryAcquireVerification());
        limiter.releaseVerification();

        limiter.recordFailure(id, caller, "one");
        assertFalse(limiter.isAllowed(id, caller, "one"));
        assertTrue(limiter.isAllowed(id, caller, "two"));
        clock.millis = 251;
        assertTrue(limiter.isAllowed(id, caller, "one"));
        limiter.recordSuccess(id, caller, "one");
        assertTrue(limiter.isAllowed(id, caller, "one"));
    }

    private static final class MutableClock extends Clock {
        private long millis;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
