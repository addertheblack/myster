package com.myster.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.general.thread.AsyncTaskTracker;
import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.thread.SimpleTaskTracker;
import com.general.util.Timer;
import com.myster.hash.FileHash;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.msdownload.MultiSourceUtilities;
import com.myster.search.AsyncNetworkCrawler.SearchIp;
import com.myster.tracker.Tracker;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;

/**
 * This class will crawl the network looking for both new servers to crawl but
 * also files with matching hash codes. The search is segregated by MysterType
 * which denotes separate conceptual networks file types like movies or music or
 * ebooks...
 */
public class MultiSourceHashSearch implements HashCrawlerManager {
    private static final Logger LOGGER = Logger.getLogger(MultiSourceHashSearch.class.getName());

    private static final int TIME_BETWEEN_CRAWLS = 10 * 60 * 1000;

    private final Map<MysterType, BatchedType> typeHashtable = new HashMap<>();
    private final Tracker tracker;
    private final MysterProtocol protocol;

    private int timeInMs;

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
    private synchronized List<SearchEntry> getEntriesForType(MysterType type) {
        return getBatchForType(type).entries;
    }

    private synchronized BatchedType getBatchForType(MysterType type) {
        BatchedType batch = typeHashtable.get(type);

        if (batch == null) {
            batch = new BatchedType();

            typeHashtable.put(type, batch);
        }

        return batch;
    }


    /**
     * Add a new HashSearchListener and associate it with a type and hash.
     * 
     * Starts the crawler for the type
     */
    public synchronized void addHash(MysterType type, FileHash hash, HashSearchListener listener) {
        LOGGER.fine("Adding hash to crawler " + hash);

        List<SearchEntry> entriesVector = getEntriesForType(type);

        for (SearchEntry searchEntry : entriesVector) {
            if (searchEntry.hash.equals(hash)) {
                return;
            }
        }

        entriesVector.add(new SearchEntry(hash, listener));


        LOGGER.fine("Size of entriesVector " + entriesVector.size());
        if ((entriesVector.size() >= 1)) {
            restartCrawler(type);
        }
    }

    /**
     * Add a new HashSearchListener and associate it with a type and hash.
     * 
     * Stops the crawler for the type if there are not more hashes to search
     */
    public synchronized void removeHash(MysterType type,
                                        FileHash hash,
                                        HashSearchListener listener) {
        LOGGER.fine("Removing hash from crawler " + hash);

        List<SearchEntry> entriesVector = getEntriesForType(type);

        entriesVector.remove(new SearchEntry(hash, listener));

        if (entriesVector.size() == 0) {
            stopCrawler(type);
        }
    }

    // asserts that the crawler is stopping
    private synchronized void stopCrawler(MysterType type) {
        LOGGER.fine("stopCrawler(" + type + ")");
        BatchedType batchedType = getBatchForType(type);

        if (batchedType.asyncTracker == null)
            return;

        INVOKER.invoke(batchedType.asyncTracker::cancel);

        batchedType.asyncTracker = null;
    }

    private synchronized void restartCrawler(MysterType type) {
        LOGGER.fine("restartCrawler(" + type + ")");

        stopCrawler(type);
        if (getEntriesForType(type).size() > 0) { // are we still relevant?
            MultiSourceUtilities.debug("Retarting crawler!");
            startCrawler(type);
        } else {
            LOGGER.fine("Not calling restartCrawler(" + type
                    + ") because there are no more hashes");
        }
    }

    // asserts that the crawler is running (ie: only "starts" the crawler if one
    // is not already running)
    private synchronized void startCrawler(final MysterType type) {
        LOGGER.fine("startCrawler(" + type + ")");
        if (tracker == null) {
            throw new NullPointerException("tracker not inited");
        }

        BatchedType batchedType = getBatchForType(type);

        if (batchedType.asyncTracker != null) {
            throw new IllegalStateException("batchedType.tracker must be null here");
        }

        final IPQueue ipQueue = new IPQueue();

        MysterServer[] top = tracker.getTop(type, 200);
        
        // when Myster is first started, pings have not yet run.. So if we get no up servers then
        // just use everything
        if (top.length==0) {
            top = tracker.getAll(type).toArray(MysterServer[]::new);
        }

        for (MysterServer s : top) {
            s.getBestAddress().ifPresent(ipQueue::addIP);
        }

        // normally the tracker would be connected to the upstream future so it could be
        // cancelled but there's not upstream. The tracker is god!
        AsyncTaskTracker asyncTaskTracker = AsyncTaskTracker.create(new SimpleTaskTracker(), INVOKER);

        batchedType.asyncTracker = asyncTaskTracker;

        List<SearchEntry> entries = new ArrayList<>(batchedType.entries);
        INVOKER.invoke(() -> {
            SearchIp searchIp = (MysterAddress address, MysterType localType) -> {
                List<PromiseFuture<String>> f = entries.stream()
                        .map(searchEntry -> protocol.getDatagram()
                                .getFileFromHash(new ParamBuilder(address), localType, searchEntry.hash)
                                .addResultListener(fileName -> {
                                    if (fileName.isEmpty()) {
                                        return;
                                    }

                                    MysterFileStub stub =
                                            new MysterFileStub(address, localType, fileName);
                                    LOGGER.fine("Found new matching file \"" + stub + "\"");
                                    searchEntry.listener.searchResult(stub);
                                })
                                .addExceptionListener(ex -> LOGGER
                                        .fine("Exception while doing UDP hash search crawler getFileFromHash("
                                                + address + ", " + localType + ") " + ex)))
                        .collect(Collectors.toList());

                LOGGER.fine("Searching for " + f.size() + " hashes");
                return PromiseFutures.all(f);
            };

            asyncTaskTracker.doAsync(() -> {
                return PromiseFuture.newPromiseFuture(context -> {
                    AsyncTaskTracker t = AsyncTaskTracker.create(new SimpleTaskTracker(), INVOKER);
                    t.setDoneListener(() -> context.setResult(null));
                    AsyncNetworkCrawler
                            .startWork(LOGGER, protocol, searchIp, type, ipQueue, tracker::addIp, t);
                });
            }).addResultListener((_) -> waitForSomeTimeThenRestart(asyncTaskTracker, type));
        });
    }

    private void waitForSomeTimeThenRestart(AsyncTaskTracker tracker, MysterType type) {
        tracker.doAsync(() -> {
            LOGGER.fine("Crawler sleeping for " + timeInMs + "ms for type " + type);
            return sleep(timeInMs).addResultListener((_) -> restartCrawler(type));
        });
    }

    private PromiseFuture<Void> sleep(int ms) {
        return PromiseFuture.newPromiseFuture(c -> {
            Timer t = new Timer(() -> c.setResult(null), ms);

            c.registerDependentTask(() -> t.cancelTimer());
        });
    }

    private static class SearchEntry {
        public final FileHash hash;

        public final HashSearchListener listener;

        public SearchEntry(FileHash hash, HashSearchListener listener) {
            this.hash = hash;
            this.listener = listener;
        }

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
        public AsyncTaskTracker asyncTracker;

        public final List<SearchEntry> entries = new ArrayList<>();
    }
}