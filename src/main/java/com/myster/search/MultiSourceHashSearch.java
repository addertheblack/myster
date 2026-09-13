package com.myster.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.general.thread.AsyncContext;
import com.general.thread.AsyncTaskTracker;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Timer;
import com.myster.hash.FileHash;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.search.AsyncNetworkCrawler.SearchIp;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;

/**
 * This class will crawl the network looking for both new servers to crawl but
 * also files with matching hash codes. The search is segregated by MysterType
 * which denotes separate conceptual networks file types like movies or music or
 * ebooks...
 *
 * <p>Public commands may be called from any thread and are queued on the crawler invoker.
 * All mutable crawl state, network-result listeners and retry callbacks are confined to that
 * invoker. Commands return before their changes take effect.
 */
public class MultiSourceHashSearch implements HashCrawlerManager {
    private static final Logger log = Logger.getLogger(MultiSourceHashSearch.class.getName());

    private static final int TIME_BETWEEN_CRAWLS = 10 * 60 * 1000;

    private final Map<MysterType, BatchedType> typeHashtable = new HashMap<>();
    private final Tracker tracker;
    private final MysterProtocol protocol;

    private volatile int timeInMs;

    private final static Invoker INVOKER = Invoker.newVThreadInvoker();

    public MultiSourceHashSearch(Tracker tracker, MysterProtocol protocol) {
        this.tracker = tracker;
        this.protocol = protocol;

        this.timeInMs = TIME_BETWEEN_CRAWLS;
    }

    /**
     * Used by unit tests only to avoid having a large 10 minute wait when
     * testing
     */
    public void setTimeBetweenCrawls(int timeInMs) {
        this.timeInMs = timeInMs;
    }

    /**
     * Gives the hashes for crawling for a given MysterType
     */
    private List<SearchEntry> getEntriesForType(MysterType type) {
        return getBatchForType(type).entries;
    }

    private BatchedType getBatchForType(MysterType type) {
        BatchedType batch = typeHashtable.get(type);

        if (batch == null) {
            batch = new BatchedType();

            typeHashtable.put(type, batch);
        }

        return batch;
    }


    /**
     * Add a new HashSearchListener and associate it with a type and hash.
     * <p>
     * Queues registration and restarts the crawler for the type.
     */
    @Override
    public void addHash(MysterType type, FileHash hash, HashSearchListener listener) {
        INVOKER.invoke(() -> addHashOnInvoker(type, hash, listener));
    }

    private void addHashOnInvoker(MysterType type, FileHash hash, HashSearchListener listener) {
        log.fine("Adding hash to crawler " + hash);

        List<SearchEntry> entriesVector = getEntriesForType(type);

        for (SearchEntry searchEntry : entriesVector) {
            if (searchEntry.hash.equals(hash)) {
                return;
            }
        }

        entriesVector.add(new SearchEntry(hash, listener));


        log.fine("Size of entriesVector " + entriesVector.size());
        if ((!entriesVector.isEmpty())) {
            restartCrawler(type);
        }
    }

    /**
     * Queues removal of a hash listener. Stops the crawler and its retry timer when no
     * hashes remain for the type.
     * If other hashes remain, the active crawl retains its entries snapshot and can still
     * notify the removed listener after removal is processed. Listeners must tolerate late results.
     */
    @Override
    public void removeHash(MysterType type,
                                        FileHash hash,
                                        HashSearchListener listener) {
        INVOKER.invoke(() -> removeHashOnInvoker(type, hash, listener));
    }

    private void removeHashOnInvoker(MysterType type, FileHash hash, HashSearchListener listener) {
        log.fine("Removing hash from crawler " + hash);

        List<SearchEntry> entriesVector = getEntriesForType(type);

        entriesVector.remove(new SearchEntry(hash, listener));

        if (entriesVector.isEmpty()) {
            stopCrawler(type);
        }
    }

    // asserts that the crawler is stopping
    private void stopCrawler(MysterType type) {
        log.fine("stopCrawler(" + type + ")");
        BatchedType batchedType = getBatchForType(type);

        if (batchedType.asyncTracker == null)
            return;

        batchedType.promise.cancel();

        batchedType.asyncTracker = null;
        batchedType.promise = null;
    }

    private void restartCrawler(MysterType type) {
        log.fine("restartCrawler(" + type + ")");

        stopCrawler(type);
        if (!getEntriesForType(type).isEmpty()) { // are we still relevant?
            startCrawler(type);
        } else {
            log.fine("Not calling restartCrawler(" + type
                    + ") because there are no more hashes");
        }
    }

    private void startCrawler(MysterType type) {
        BatchedType batchedType = getBatchForType(type);
        PromiseFuture<Void> crawl = PromiseFuture.newPromiseFuture((AsyncContext<Void> context) -> {
            AsyncTaskTracker<Void> taskTracker = AsyncTaskTracker.create(context, INVOKER,
                    Optional.of(finishedTracker -> finishedTracker.setResult(null)));
            batchedType.asyncTracker = taskTracker;
            crawlNetwork(type, batchedType, taskTracker);
        });
        batchedType.promise = crawl.mapAsync(_ -> sleep(timeInMs), INVOKER)
                .withInvoker(INVOKER)
                .addResultListener(_ -> restartCrawler(type))
                .addStandardExceptionHandler();
    }

    private void crawlNetwork(MysterType type,
                              BatchedType batchedType,
                              AsyncTaskTracker<Void> taskTracker) {
        final IPQueue ipQueue = new IPQueue();

        MysterServer[] top = tracker.getTop(type, 200);

        // when Myster is first started, pings have not yet run.. So if we get no up servers then
        // just use everything
        if (top.length == 0) {
            top = tracker.getAll(type).toArray(MysterServer[]::new);
        }

        for (MysterServer s : top) {
            s.getBestAddress().ifPresent(ipQueue::addIP);
        }

        AsyncNetworkCrawler.startWork(log, protocol, createSearchIp(batchedType, taskTracker),
                type, ipQueue, tracker::addIp, taskTracker);

        // With no usable seeds, no child task can trigger the exhaustion listener.
        if (ipQueue.getNumberOfItemsProcessed() == 0) {
            taskTracker.setResult(null);
        }
    }

    private SearchIp createSearchIp(BatchedType batchedType, AsyncTaskTracker<Void> taskTracker) {
        List<SearchEntry> entries = new ArrayList<>(batchedType.entries);
        SearchIp searchIp = (MysterAddress address, MysterType localType) -> {
            List<PromiseFuture<String>> f = entries.stream()
                    .map(searchEntry -> taskTracker.doAsync(() -> protocol.getDatagram()
                            .getFileFromHash(new ParamBuilder(address), localType, searchEntry.hash))
                            .addResultListener(fileName -> {
                                if (taskTracker.isDone() || fileName.isEmpty()) {
                                    return;
                                }

                                MysterFileStub stub =
                                        new MysterFileStub(address, localType, fileName);
                                log.fine("Found new matching file \"" + stub + "\"");
                                searchEntry.listener.searchResult(stub);
                            })
                            .addExceptionListener(ex -> log
                                    .fine("Exception while doing UDP hash search crawler getFileFromHash("
                                            + address + ", " + localType + ") " + ex)))
                    .collect(Collectors.toList());

            log.fine("Searching for " + f.size() + " hashes");
            return PromiseFutures.allCallResults(f);
        };
        return searchIp;
    }

    private PromiseFuture<Void> sleep(int ms) {
        return PromiseFuture.newPromiseFuture(c -> {
            Timer t = new Timer(() -> c.setResult(null), ms);

            c.trackForCancellation(t::cancelTimer);
        });
    }

    private record SearchEntry(FileHash hash, HashSearchListener listener) {
        public boolean equals(Object o) {
            SearchEntry other;

            try {
                other = (SearchEntry) o;
            } catch (ClassCastException _) {
                return false;
            }

            return (other.listener.equals(listener) && other.hash.equals(hash));
        }
    }

    private static class BatchedType {
        public AsyncTaskTracker<Void> asyncTracker;

        public final List<SearchEntry> entries = new ArrayList<>();
        public PromiseFuture<Void> promise;
    }
}
