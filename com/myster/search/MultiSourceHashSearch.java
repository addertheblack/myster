package com.myster.search;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import com.myster.client.stream.MultiSourceUtilities;
import com.myster.client.stream.StandardSuite;
import com.myster.client.stream.UnknownProtocolException;
import com.myster.hash.FileHash;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.type.MysterType;

public class MultiSourceHashSearch implements MysterSearchClientSection {
    //STATIC SUB SYSTEM

    private static final int TIME_BETWEEN_CRAWLS = 10 * 60 * 1000;

    private static final Hashtable typeHashtable = new Hashtable();

    private synchronized static Vector getEntriesForType(MysterType type) {
        return getBatchForType(type).entries;
    }

    private synchronized static BatchedType getBatchForType(MysterType type) {
        BatchedType batch = (BatchedType) typeHashtable.get(type);

        if (batch == null) {
            batch = new BatchedType();

            typeHashtable.put(type, batch);
        }

        return batch;
    }

    public synchronized static void addHash(MysterType type, FileHash hash,
            HashSearchListener listener) {
        Vector entriesVector = getEntriesForType(type);

        entriesVector.addElement(new SearchEntry(hash, listener));

        if ((entriesVector.size() == 1)) {
            startCrawler(type);
        }
    }

    public synchronized static void removeHash(MysterType type, FileHash hash,
            HashSearchListener listener) {
        Vector entriesVector = getEntriesForType(type);

        entriesVector.removeElement(new SearchEntry(hash, listener));

        if (entriesVector.size() == 0) {
            stopCrawler(type);
        }
    }

    private synchronized static SearchEntry[] getSearchEntries(MysterType type) {
        Vector entriesVector = getEntriesForType(type);

        SearchEntry[] entries = new SearchEntry[entriesVector.size()];

        for (int i = 0; i < entries.length; i++) {
            entries[i] = (SearchEntry) entriesVector.elementAt(i);
        }

        return entries;
    }

    // asserts that the crawler is stopping
    private synchronized static void stopCrawler(MysterType type) {
        BatchedType batchedType = getBatchForType(type);

        if (batchedType.crawler == null)
            return;

        batchedType.crawler.flagToEnd();

        batchedType.crawler = null;
    }

    private synchronized static void restartCrawler(MysterType type) {
        stopCrawler(type);
        if (getEntriesForType(type).size() > 0) { // are we still relevent?
            MultiSourceUtilities.debug("Retarting crawler!");
            startCrawler(type);
        }
    }

    // asserts that the crawler is running (ie: only "starts" the crawler if one
    // is not already running)
    private synchronized static void startCrawler(final MysterType type) {
        BatchedType batchedType = getBatchForType(type);

        if (batchedType.crawler != null)
            return;

        final IPQueue ipQueue = new IPQueue();

        batchedType.crawler = new CrawlerThread(new MultiSourceHashSearch(), //note..
                // will
                // not
                // restart
                // when
                // crawl
                // is
                // done
                type, ipQueue, new com.myster.util.Sayable() {
                    public void say(String string) {
                        MultiSourceUtilities.debug("Hash Search -> " + string);
                    }
                });
        new Thread() {
            public void run() {
                com.myster.tracker.MysterServer[] iparray = com.myster.tracker.IPListManagerSingleton
                        .getIPListManager().getTop(type, 50);

                String[] startingIps = com.myster.tracker.IPListManagerSingleton.getIPListManager()
                        .getOnRamps();

                int counter = 0;
                for (int i = 0; (i < iparray.length) && (iparray[i] != null); i++) {
                    ipQueue.addIP(iparray[i].getAddress());
                    counter++;
                }

                if (counter < 3) {
                    for (int i = 0; i < startingIps.length; i++) {
                        try {
                            ipQueue.addIP(new MysterAddress(startingIps[i]));
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                synchronized (MultiSourceHashSearch.class) {
                    BatchedType batchedType = getBatchForType(type);
                    if (batchedType.crawler!=null)
                        batchedType.crawler.start();
                }
            }
        }.start();
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
            } catch (ClassCastException ex) {
                return false;
            }

            return (other.listener.equals(listener) && other.hash.equals(hash));
        }
    }

    private static class BatchedType {
        public final Vector entries = new Vector(10, 10);

        public CrawlerThread crawler;
    }

    //OBJECT SYSTEM

    public void start() {
        // lalalala...
    }

    public void search(MysterSocket socket, MysterAddress address, MysterType type)
            throws IOException {
        MultiSourceUtilities.debug("Hash Search -> Searching " + address);

        SearchEntry[] searchEntries = getSearchEntries(type);

        for (int i = 0; i < searchEntries.length; i++) {
            searchEntries[i].listener.fireEvent(new HashSearchEvent(HashSearchEvent.START_SEARCH,
                    null));
        }

        try {

            //This loops goes through each entry one at a time. it oculd be
            // optimised by sending them
            //in a batch in the same way as file stats are done when downloaded
            // off the server after a search
            for (int i = 0; i < searchEntries.length; i++) {
                SearchEntry searchEntry = searchEntries[i];

                MultiSourceUtilities.debug("HashSearch -> Searching has " + searchEntry.hash);

                String fileName = StandardSuite.getFileFromHash(socket, type, searchEntry.hash);

                if (!fileName.equals("")) {
                    MultiSourceUtilities.debug("HASH SEARCH FOUND FILE -> " + fileName);
                    searchEntry.listener.fireEvent(new HashSearchEvent(
                            HashSearchEvent.SEARCH_RESULT, new MysterFileStub(address, type,
                                    fileName)));
                }
            }
        } catch (UnknownProtocolException ex) {
            MultiSourceUtilities.debug("Hash Search -> Server " + address
                    + " doesn't understand search by hash connection section.");
        }

        for (int i = 0; i < searchEntries.length; i++) {
            searchEntries[i].listener.fireEvent(new HashSearchEvent(HashSearchEvent.END_SEARCH,
                    null));
        }
    }

    public void endSearch(final MysterType type) {
        MultiSourceUtilities.debug("HashSearch -> Search thread has died");
    }

    boolean endFlag = false;

    public void flagToEnd() {
        endFlag = true;
        MultiSourceUtilities.debug("HashSearch -> Search was told to end");

    }

    public void end() {
        //why is this here.. ?
    }

    public void searchedAll(final MysterType type) {
        MultiSourceUtilities.debug("Hash Search -> Crawler has crawled the whole network!");
        com.general.util.Timer timer = new com.general.util.Timer(new Runnable() {
            public void run() {
                synchronized (MultiSourceHashSearch.class) { //sigh..
                    // this is
                    // to fix a
                    // dumb
                    // race
                    // condition
                    // that can
                    // happen
                    if (!endFlag) {
                        restartCrawler(type);
                    }
                }
            }
        }, TIME_BETWEEN_CRAWLS);
    }
}