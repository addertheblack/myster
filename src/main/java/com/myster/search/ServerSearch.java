package com.myster.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.general.thread.AsyncTaskTracker;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.tracker.ui.KnownServerSource;
import com.myster.type.MysterType;
import com.myster.util.Sayable;

/**
 * Finite server-name/description search in one type's network. Candidates come only from that
 * type's tracker list and top-server crawl. Each visited address uses cached stats before the
 * pool-backed resolver; the complete pool is never enumerated.
 *
 * <p>Result batches and progress are delivered on the EDT, queued before successful completion
 * listeners. Each delivery checks the returned promise's current cancellation state on the EDT,
 * so cancellation also suppresses queued callbacks. It does not stop a pool refresh shared with
 * other callers.
 */
public final class ServerSearch {
    private static final Logger log = Logger.getLogger(ServerSearch.class.getName());
    private static final Invoker INVOKER = Invoker.newVThreadInvoker();
    private static final int BATCH_SIZE = 64;

    private final MysterProtocol protocol;
    private final Tracker tracker;
    private final KnownServerSource source;

    public ServerSearch(MysterProtocol protocol, Tracker tracker, KnownServerSource source) {
        this.protocol = Objects.requireNonNull(protocol);
        this.tracker = Objects.requireNonNull(tracker);
        this.source = Objects.requireNonNull(source);
    }

    /**
     * Starts an independent crawl. Matching is a trimmed, case-insensitive substring in either
     * field; blank input matches every encountered server advertising the type, even with zero files.
     * The returned promise completes on exhaustion, including an empty/failed bootstrap.
     */
    public PromiseFuture<Void> search(MysterType type, String query,
                                       Consumer<List<ServerSearchResult>> results, Sayable progress) {
        String term = query.trim().toLowerCase(Locale.ROOT);
        String[] onramps = Tracker.getOnRamps();
        return PromiseFuture.newPromiseFuture(context -> {
            Crawl crawl = new Crawl(type, term, results, progress);
            crawl.taskTracker = AsyncTaskTracker.create(context, INVOKER,
                    Optional.of(finished -> {
                        crawl.flush();
                        finished.setResult(null);
                    }));
            INVOKER.invoke(() -> crawl.start(onramps));
        });
    }

    private record Seeds(List<MysterAddress> addresses, int rankedCount) {}

    private Seeds seeds(MysterType type) {
        List<MysterAddress> addresses = new ArrayList<>();
        MysterServer[] top = tracker.getTop(type, 50);
        if (top != null) {
            for (MysterServer server : top) {
                if (server != null) {
                    server.getBestAddress().ifPresent(addresses::add);
                }
                if (addresses.size() == 50) {
                    break;
                }
            }
        }
        int rankedCount = addresses.size();
        if (addresses.isEmpty()) {
            List<MysterServer> all = tracker.getAll(type);
            if (all != null) {
                all.forEach(server -> server.getBestAddress().ifPresent(addresses::add));
            }
        }
        return new Seeds(List.copyOf(addresses), rankedCount);
    }

    private final class Crawl {
        private final MysterType type;
        private final String term;
        private final Consumer<List<ServerSearchResult>> results;
        private final Sayable progress;
        private final IPQueue queue = new IPQueue();
        private final Set<MysterIdentity> emitted = new HashSet<>();
        private final List<ServerSearchResult> pending = new ArrayList<>();
        private AsyncTaskTracker<Void> taskTracker;
        private boolean flushQueued;
        private int checked;

        private Crawl(MysterType type, String term, Consumer<List<ServerSearchResult>> results,
                       Sayable progress) {
            this.type = type;
            this.term = term;
            this.results = results;
            this.progress = progress;
        }

        private void start(String[] onramps) {
            if (taskTracker.isDone()) {
                return;
            }
            taskTracker.doAsync(() -> PromiseFutures.execute(() -> seeds(type)))
                    .addResultListener(seeds -> {
                        if (taskTracker.isDone()) {
                            return;
                        }
                        seeds.addresses().forEach(queue::addIP);
                        if (seeds.rankedCount() <= 4) {
                            for (String onramp : onramps) {
                                taskTracker.doAsync(() -> PromiseFutures.execute(
                                        () -> MysterAddress.createMysterAddress(onramp)))
                                        .addResultListener(address -> {
                                            if (!taskTracker.isDone()) {
                                                queue.addIP(address);
                                                crawl();
                                            }
                                        })
                                        .addExceptionListener(ex -> log.fine("Onramp lookup failed: " + ex));
                            }
                        }
                        crawl();
                    })
                    .addExceptionListener(taskTracker::setException);
        }

        private void crawl() {
            AsyncNetworkCrawler.startWork(log, protocol, (address, _) -> stats(address),
                    type, queue, _ -> {}, taskTracker);
        }

        private PromiseFuture<Void> stats(MysterAddress address) {
            MysterServer cached = tracker.getQuickServerStats(address);
            PromiseFuture<MysterServer> server = cached == null ? source.resolveServer(address)
                    : PromiseFuture.newPromiseFuture(cached);
            return server.mapAsync(value -> {
                if (!taskTracker.isDone()) {
                    match(value);
                }
                return PromiseFuture.<Void>newPromiseFuture((Void) null);
            }, INVOKER);
        }

        private void match(MysterServer server) {
            checked++;
            String name = Objects.requireNonNullElse(server.getServerName(), "");
            String description = Objects.requireNonNullElse(server.getServerDescription(), "");
            if (server.knowsAboutType(type)
                    && (name.toLowerCase(Locale.ROOT).contains(term)
                        || description.toLowerCase(Locale.ROOT).contains(term))) {
                server.getBestAddress().ifPresent(address -> {
                    if (emitted.add(server.getIdentity())) {
                        pending.add(new ServerSearchResult(server.getIdentity(), address, name, description,
                                server.getNumberOfFiles(type), server.getPingTime()));
                    }
                });
            }
            if (pending.size() >= BATCH_SIZE) {
                flush();
            } else if (!flushQueued) {
                flushQueued = true;
                INVOKER.invoke(() -> {
                    if (!taskTracker.isDone()) {
                        flush();
                    }
                });
            }
        }

        private void flush() {
            flushQueued = false;
            if (taskTracker.isCancelled()) {
                pending.clear();
                return;
            }
            List<ServerSearchResult> batch = List.copyOf(pending);
            pending.clear();
            String status = "Checked " + checked + " servers...";
            Util.invokeLater(() -> {
                if (!taskTracker.isCancelled()) {
                    if (!batch.isEmpty()) {
                        results.accept(batch);
                    }
                    if (!taskTracker.isCancelled()) {
                        progress.say(status);
                    }
                }
            });
        }
    }
}
