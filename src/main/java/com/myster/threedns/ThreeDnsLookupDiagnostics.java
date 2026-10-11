package com.myster.threedns;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import com.myster.net.datagram.DatagramEncryptUtil.DecryptionException;
import com.myster.net.datagram.TimeoutException;
import com.myster.net.stream.client.UnknownProtocolException;

/**
 * Immutable observations from one lookup, bounded by its query limit. A missing exact seed means
 * no usable exact candidate was included in the tracker snapshot, not that the identity is unknown.
 * In-flight attempts had no completed response when the terminal result was chosen.
 */
public record ThreeDnsLookupDiagnostics(ThreeDnsAddressCandidateSet seeds,
                                        List<QueryAttempt> attempts,
                                        Duration elapsed) {
    public enum Outcome { IN_FLIGHT, VERIFIED, FAILED }

    /** A query's terminal observation; only a failed query has an I/O failure cause. */
    public record QueryAttempt(ThreeDnsAddressCandidate candidate,
                               Outcome outcome,
                               Optional<IOException> failure) {
        public QueryAttempt {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(failure, "failure");
            if ((outcome == Outcome.FAILED) != failure.isPresent()) {
                throw new IllegalArgumentException("Only failed queries require a cause");
            }
        }
    }

    public ThreeDnsLookupDiagnostics {
        Objects.requireNonNull(seeds, "seeds");
        attempts = List.copyOf(attempts);
        Objects.requireNonNull(elapsed, "elapsed");
    }

    static ThreeDnsLookupDiagnostics empty() {
        return new ThreeDnsLookupDiagnostics(ThreeDnsAddressCandidateSet.empty(), List.of(), Duration.ZERO);
    }

    public int seedCount() {
        var candidates = new HashSet<>(seeds.left());
        candidates.addAll(seeds.right());
        seeds.exact().ifPresent(candidates::add);
        return candidates.size();
    }

    /** Returns copyable routing observations without public keys or invitation credentials. */
    public String describe() {
        StringBuilder text = new StringBuilder("Elapsed: ").append(elapsed.toMillis()).append(" ms\n")
                .append("Tracker snapshot: ").append(seedCount()).append(" usable candidates\n")
                .append("Usable exact tracker candidate: ")
                .append(seeds.exact().map(ThreeDnsLookupDiagnostics::describeCandidate).orElse("none"))
                .append('\n');
        seeds.left().forEach(candidate -> text.append("Tracker neighbor: ")
                .append(describeCandidate(candidate)).append('\n'));
        seeds.right().forEach(candidate -> text.append("Tracker neighbor: ")
                .append(describeCandidate(candidate)).append('\n'));
        text.append("Queries attempted: ").append(attempts.size()).append('\n');
        for (QueryAttempt attempt : attempts) {
            text.append(describeCandidate(attempt.candidate())).append(" — ").append(attempt.outcome());
            attempt.failure().ifPresent(failure -> text.append(": ").append(describeFailure(failure)));
            text.append('\n');
        }
        return text.toString();
    }

    private static String describeCandidate(ThreeDnsAddressCandidate candidate) {
        return candidate.cid() + " at " + candidate.address();
    }

    private static String describeFailure(IOException failure) {
        String reason = switch (failure) {
            case TimeoutException _ -> "No authenticated response received before the transaction timeout";
            case DecryptionException _ -> "Response could not be decrypted/authenticated";
            case UnknownProtocolException protocol -> "Remote protocol error " + protocol.getError();
            default -> failure.getClass().getName();
        };
        return reason + " (" + failure.getClass().getName() + "): " + failure.getMessage();
    }
}
