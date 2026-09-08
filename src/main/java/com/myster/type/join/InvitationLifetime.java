package com.myster.type.join;

import java.time.Duration;

/** Expiry choices offered when creating a private-type invitation. */
public enum InvitationLifetime {
    TWENTY_FOUR_HOURS("24 hours", Duration.ofHours(24)),
    SEVEN_DAYS("7 days", Duration.ofDays(7)),
    THIRTY_DAYS("30 days", Duration.ofDays(30));

    private final String label;
    private final Duration duration;

    InvitationLifetime(String label, Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }

    @Override
    public String toString() {
        return label;
    }
}
