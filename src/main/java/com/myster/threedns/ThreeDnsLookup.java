package com.myster.threedns;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.general.thread.AsyncContext;
import com.general.thread.AsyncTaskTracker;
import com.general.thread.Cancellable;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.tracker.Tracker;

/**
 * Resolves a server CID through bounded, identity-verifying 3DNS traversal.
 *
 * <p>The production API snapshots initial candidates from {@link Tracker};
 * callers provide only a target CID. Every seed and returned hint must complete
 * an expected-key query before it can become a verified result. Cancelling the
 * returned future cancels the deadline and all peer queries owned by the
 * lookup.
 */
public final class ThreeDnsLookup {
    private static final Invoker LOOKUP_INVOKER = Invoker.newVThreadInvoker();
    private static final Limits DEFAULT_LIMITS = new Limits(
            ThreeDnsAddressCandidateSet.DEFAULT_PER_SIDE_LIMIT,
            64,
            32,
            2,
            2,
            Duration.ofSeconds(60));

    private final ThreeDnsSeedProvider seedProvider;
    private final PeerQuery peerQuery;
    private final Limits limits;
    private final DeadlineScheduler deadlineScheduler;
    private final Invoker lookupInvoker;

    public ThreeDnsLookup(Tracker tracker, ThreeDnsPeerClient peerClient) {
        this(Objects.requireNonNull(tracker, "tracker")::getThreeDnsCandidatesForTarget,
             Objects.requireNonNull(peerClient, "peerClient")::findClosest,
             DEFAULT_LIMITS,
             DefaultDeadlineScheduler.INSTANCE,
             LOOKUP_INVOKER);
    }

    ThreeDnsLookup(ThreeDnsSeedProvider seedProvider,
                   PeerQuery peerQuery,
                   Limits limits,
                   DeadlineScheduler deadlineScheduler) {
        this(seedProvider, peerQuery, limits, deadlineScheduler, LOOKUP_INVOKER);
    }

    ThreeDnsLookup(ThreeDnsSeedProvider seedProvider,
                   PeerQuery peerQuery,
                   Limits limits,
                   DeadlineScheduler deadlineScheduler,
                   Invoker lookupInvoker) {
        this.seedProvider = Objects.requireNonNull(seedProvider, "seedProvider");
        this.peerQuery = Objects.requireNonNull(peerQuery, "peerQuery");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.deadlineScheduler = Objects.requireNonNull(deadlineScheduler, "deadlineScheduler");
        this.lookupInvoker = Objects.requireNonNull(lookupInvoker, "lookupInvoker");
    }

    /**
     * Starts one lookup using a single target-specific Tracker snapshot.
     * Candidate failures are absorbed while another eligible route remains.
     *
     * @param target server CID to resolve
     * @return cancellable future containing an exact or bounded terminal result
     */
    public PromiseFuture<ThreeDnsLookupResult> resolve(ServerCid target) {
        Objects.requireNonNull(target, "target");
        return PromiseFuture.newPromiseFuture(context -> lookupInvoker.invoke(() -> {
            AsyncTaskTracker taskTracker = AsyncTaskTracker.create(context, lookupInvoker);
            if (context.isCancelled()) {
                return;
            }
            new LookupState(target, context, taskTracker).start();
        }));
    }

    @FunctionalInterface
    interface PeerQuery {
        PromiseFuture<ThreeDnsVerifiedQueryResult> findClosest(
                ThreeDnsAddressCandidate candidate,
                ServerCid target,
                int perSideLimit);
    }

    @FunctionalInterface
    interface DeadlineScheduler {
        Cancellable schedule(Runnable task, Duration delay);
    }

    record Limits(int perSideLimit,
                  int maxQueuedEntries,
                  int maxQueryAttempts,
                  int maxInFlight,
                  int maxAddressesPerIdentity,
                  Duration deadline) {
        Limits {
            if (perSideLimit <= 0
                    || perSideLimit > ThreeDnsAddressCandidateSet.MAX_PER_SIDE_LIMIT) {
                throw new IllegalArgumentException("perSideLimit must be between 1 and "
                        + ThreeDnsAddressCandidateSet.MAX_PER_SIDE_LIMIT);
            }
            if (maxQueuedEntries <= 0
                    || maxQueryAttempts <= 0
                    || maxInFlight <= 0
                    || maxAddressesPerIdentity <= 0) {
                throw new IllegalArgumentException("Lookup limits must be positive");
            }
            Objects.requireNonNull(deadline, "deadline");
            if (deadline.isZero() || deadline.isNegative()) {
                throw new IllegalArgumentException("deadline must be positive");
            }
        }
    }

    private final class LookupState {
        private final ServerCid target;
        private final AsyncContext<ThreeDnsLookupResult> context;
        private final ThreeDnsLookupFrontier frontier;
        private final AsyncTaskTracker taskTracker;

        private Cancellable deadlineTask;
        private VerifiedThreeDnsPeer closestVerified;
        private int activeQueries;
        private int queryAttempts;
        private boolean finished;

        private LookupState(ServerCid target,
                            AsyncContext<ThreeDnsLookupResult> context,
                            AsyncTaskTracker taskTracker) {
            this.target = target;
            this.context = context;
            this.taskTracker = taskTracker;
            frontier = new ThreeDnsLookupFrontier(target,
                                                  limits.maxQueuedEntries(),
                                                  limits.maxAddressesPerIdentity());
        }

        private void start() {
            taskTracker.setDoneListener(this::tasksDrained);
            ThreeDnsAddressCandidateSet seeds =
                    seedProvider.candidatesFor(target, limits.perSideLimit());
            frontier.addAll(Objects.requireNonNull(seeds, "seed provider result"));
            if (frontier.queuedCount() == 0) {
                finishResult(ThreeDnsLookupResult.exhausted(target, Optional.empty()));
                return;
            }
            deadlineTask = deadlineScheduler.schedule(
                    () -> lookupInvoker.invoke(this::deadlineReached),
                    limits.deadline());
            context.trackForCancellation(deadlineTask);
            pump();
        }

        private void querySucceeded(ThreeDnsAddressCandidate candidate,
                                    ThreeDnsVerifiedQueryResult result) {
            requireMatchingResponder(candidate, result.responder());
            frontier.markVerified(candidate);
            VerifiedThreeDnsPeer responder = result.responder();
            if (responder.cid().equals(target)) {
                finishResult(ThreeDnsLookupResult.exact(target, responder));
                return;
            }

            if (closestVerified == null
                    || target.comparePredecessorDistance(responder.cid(), closestVerified.cid()) < 0) {
                closestVerified = responder;
            }
            frontier.addAll(result.candidates());
        }

        private void queryFailed(ThreeDnsAddressCandidate candidate) {
            frontier.markFailed(candidate);
        }

        private void queryFinished() {
            activeQueries--;
            pump();
        }

        private void requireMatchingResponder(ThreeDnsAddressCandidate candidate,
                                              VerifiedThreeDnsPeer responder) {
            if (!candidate.identity().equals(responder.identity())
                    || !candidate.cid().equals(responder.cid())
                    || !candidate.address().equals(responder.address())) {
                throw new IllegalStateException("Peer query verified a different responder");
            }
        }

        private void deadlineReached() {
            if (finished || taskTracker.isCancelled() || context.isCancelled()) {
                return;
            }
            finishResult(ThreeDnsLookupResult.bounded(
                    ThreeDnsLookupResult.Status.DEADLINE_REACHED,
                    target,
                    Optional.ofNullable(closestVerified)));
        }

        private void pump() {
            if (finished || taskTracker.isCancelled() || context.isCancelled()) {
                return;
            }

            Optional<ServerCid>  closestCid = Optional.ofNullable(closestVerified)
                    .map(VerifiedThreeDnsPeer::cid);
            while (!finished
                    && !taskTracker.isCancelled()
                    && !context.isCancelled()
                    && activeQueries < limits.maxInFlight()
                    && queryAttempts < limits.maxQueryAttempts()) {
                Optional<ThreeDnsAddressCandidate> next = frontier.pollEligible(closestCid);
                if (next.isEmpty()) {
                    break;
                }
                launch(next.get());
                closestCid = Optional.ofNullable(closestVerified).map(VerifiedThreeDnsPeer::cid);
            }
        }

        private void launch(ThreeDnsAddressCandidate candidate) {
            frontier.markLaunched(candidate);
            activeQueries++;
            queryAttempts++;
            taskTracker.doAsync(() -> Objects.requireNonNull(
                    peerQuery.findClosest(candidate, target, limits.perSideLimit()),
                    "peer query result"))
                    .addResultListener(result -> querySucceeded(candidate, result))
                    .addExceptionListener(_ -> queryFailed(candidate))
                    .addFinallyListener(this::queryFinished);
        }

        private void tasksDrained() {
            if (finished || taskTracker.isCancelled() || context.isCancelled()) {
                return;
            }
            Optional<ServerCid> closestCid = Optional.ofNullable(closestVerified)
                    .map(VerifiedThreeDnsPeer::cid);
            if (queryAttempts >= limits.maxQueryAttempts()
                    && frontier.hasEligible(closestCid)) {
                finishResult(ThreeDnsLookupResult.bounded(
                        ThreeDnsLookupResult.Status.QUERY_LIMIT_REACHED,
                        target,
                        Optional.ofNullable(closestVerified)));
                return;
            }
            finishResult(ThreeDnsLookupResult.exhausted(
                    target,
                    Optional.ofNullable(closestVerified)));
        }

        private void finishResult(ThreeDnsLookupResult result) {
            if (finished) {
                return;
            }
            finished = true;
            context.setResult(result);
            taskTracker.cancel();
            cancelDeadline();
        }

        private void cancelDeadline() {
            if (deadlineTask != null) {
                deadlineTask.cancel();
            }
        }
    }

    private enum DefaultDeadlineScheduler implements DeadlineScheduler {
        INSTANCE;

        private static final ScheduledThreadPoolExecutor EXECUTOR = createExecutor();

        private static ScheduledThreadPoolExecutor createExecutor() {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                    1,
                    Thread.ofPlatform().daemon(true).name("ThreeDnsLookupDeadline").factory());
            executor.setRemoveOnCancelPolicy(true);
            return executor;
        }

        @Override
        public Cancellable schedule(Runnable task, Duration delay) {
            var scheduled = EXECUTOR.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
            return () -> scheduled.cancel(false);
        }
    }
}
