/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 * 
 * 
 *  
 */

package com.myster.search;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Vector;

import com.general.thread.CallListener;
import com.general.thread.CancellableCallable;
import com.general.thread.Future;
import com.general.util.Util;
import com.myster.client.datagram.StandardDatagramSuite;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterSocketPool;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;
import com.myster.util.Sayable;
import com.sun.java.util.collections.HashSet;
import com.sun.java.util.collections.Iterator;
import com.sun.java.util.collections.Set;

/**
 * Implements the Myster search algorithm using the protocol stack in StandardSuite and
 * StandardDatagramSuite.
 * <p>
 * This object is setup to synchronize all its activities using the event thread. All callback from
 * asynchrnous functions arrive on the event thread. All interractions with the outside world with
 * interrnal data structures re also done on the event thread.
 */
public class MysterSearch {
    /** Contains the object to pass status messages to. */
    private Sayable msg;

    /** Contains the type to search on. */
    private MysterType type;

    /** Contains the stream based searcher algorithm code. */
    private StandardMysterSearch searcher;

    /**
     * Is set to true if some one has requested that this search be stopped.
     * <p>
     * <b>NOTE: The is a delay between when the search is requested to be stopped and when the
     * search actually stops. That is the "endFlag" and the isDone flag are not set at the same
     * time. <b>
     */
    private boolean endFlag = false;

    /** Is set to true when there are no more searches left outstanding (when the search is over). */
    private boolean isDone = false; //we're done... start shutting down.

    /** Contains the search string being used to search. */
    private String searchString;

    /** Contains the search result event listener for this search. */
    private SearchResultListener listener;

    /** Contains the number of servers that have been SUCCESSFULLY searched. */
    private volatile int serversSearched = 0;

    /** Contains Future objects for each asynchronous call still outstanding. */
    private Set outStandingFutures;

    public MysterSearch(SearchResultListener listener, Sayable msg, MysterType type,
            String searchString) {
        this.msg = msg;
        this.searcher = new StandardMysterSearch(searchString, listener);
        this.type = type;
        this.searchString = searchString;
        this.listener = listener;

        outStandingFutures = new HashSet();
    }

    /**
     * Call this routine to start the search off. The search is completely asynchronous so this
     * routine returns immediately. The search notifies listeners of important events during the
     * search.
     */
    public void run() {
        msg.say("SEARCH: Starting Search..");
        searcher.start();

        Util.invoke(new Runnable() {
            public void run() {
                IPQueue queue = createPrimedIpQueue();
                processNewAddresses(queue);

                //if this is not here and we are cancelled too early then
                //no one will call removeFuture to trigger a checkForDone().
                //We can check here because we are assured that there should
                //be some futures available because they were just created above.
                //If they haven't been created then we were cancelled before we
                //could create any and should therefor end NOW.
                checkForDone();
            }
        });
    }

    /**
     * Call this routine to request the file search to end. While there is a lag between when this
     * command is issued and when the searching actually stops (due to some technical issues) this
     * object guarantees that the listener will not receiving any new search results or search
     * result stats updates after this function returns.
     */
    public void flagToEnd() {
        synchronized (this) {
            endFlag = true;
        }

        searcher.flagToEnd();

        Util.invoke(new Runnable() {
            public void run() {
                for (Iterator iter = outStandingFutures.iterator(); iter.hasNext();) {
                    Future future = (Future) iter.next();
                    future.cancel();
                }
            }
        });
    }

    /**
     * This routine cancels the search and blocks until the search is completely over (until
     * isDone() returns true). This blocking could theoretically last up to 90 seconds (because we
     * must wait for connection attempts to time out)!
     */
    public void end() {
        msg.say("Stopping previous search threads (if any)..");
        //Stops all previous threads
        flagToEnd();

        waitForEnd();
    }

    /**
     * This routine will block the caller thread until this search really ends (until isDone()
     * returns true).
     */
    public synchronized void waitForEnd() {
        if (isDone)
            return;

        try {
            while (!isDone) {
                wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns whether or not this search has been cancelled using flagToEnd().
     * 
     * @return true if search has been cancelled.
     */
    public boolean isCancelled() {
        return endFlag;
    }

    /**
     * Returns whether or not this search has heard back from all its outstanding asynchronous
     * function calls. This can happen if the search has exhausted all servers on the network or the
     * search was cancelled and all all asynchronous function calls have returned (been accounted
     * for).
     * <p>
     * Usually one does not need to know when a search is "done()" if a search has been cancelled
     * but the behavior is done this way to allow for easy testing of this object and the
     * asynchronous function call API to make sure that the API isn't leaking function calls (that
     * is to make sure all asynchronous function calls return).
     * 
     * @return true is search is done.
     */
    public boolean isDone() {
        return isDone;
    }

    /**
     * This version starts the process for searching for an address. This function must be called on
     * the event thread.
     * 
     * @param address
     *            to search
     * @param ipQueue
     *            being used by this object
     * @throws IOException
     *             if UDP sub system could not be started.
     */
    private void searchThisIp(final MysterAddress address, final IPQueue ipQueue)
            throws IOException {
        if (endFlag)
            return;

        ADD_TOP_TEN: {
            CancellableCallableRemover remover = new UdpTopTen(ipQueue);
            Future topTenFuture = StandardDatagramSuite.getTopServers(address, type, remover);
            remover.setFuture(topTenFuture);
            outStandingFutures.add(topTenFuture);
        }

        ADD_SEARCH: {
            CancellableCallableRemover remover = new UdpSearch(address);
            Future searchFuture = StandardDatagramSuite.getSearch(address, type, searchString,
                    remover);
            remover.setFuture(searchFuture);
            outStandingFutures.add(searchFuture);
        }
    }

    /**
     * Starts off the process for dealing with file stats information for a list of files. Currently
     * this is done using a streamed connection, however in future the datagram version could be
     * used.
     * 
     * @param address
     * @param mysterSearchResults
     */
    private void dealWithFileStats(MysterAddress address, MysterSearchResult[] mysterSearchResults) {
        queueStreamSection(new StreamFileStats(address, mysterSearchResults));
    }

    /**
     * Queues up a stream section to asynchronously execute.
     * 
     * @param section
     *            to execute.
     */
    private void queueStreamSection(final StreamSection section) {
        Util.invoke(new Runnable() {
            public void run() {
                if (endFlag)
                    return;
                CancellableCallableRemover remover = new CancellableCallableRemover();
                Future future = MysterSocketPool.getInstance().execute(section, remover);
                remover.setFuture(future);
                outStandingFutures.add(future);
            }
        });
    }

    /**
     * Queues up a stream search to execute asynchronously.
     * 
     * @param address
     *            to search
     * @param type
     *            to search on
     */
    private void queueStreamSearch(final MysterAddress address, final MysterType type) {
        queueStreamSection(new StreamSearch(address, type));
    }

    /**
     * Deals with the code required to keep the IPQueue full on untried server addresses.
     * 
     * processNewAddresses() starts a "search" on each new, untried server it finds in the IPQueue.
     * 
     * @param ipQueue
     *            to explore
     */
    private void processNewAddresses(final IPQueue ipQueue) {
        if (endFlag)
            return;

        for (;;) {
            MysterAddress address = ipQueue.getNextIP();

            if (address == null)
                return; // humm, end of da line... no point in
            // going on.

            try {
                searchThisIp(address, ipQueue);
            } catch (IOException ex) {
                // humm.. an exception.. Is UDP down?
                ex.printStackTrace();
            }
        }
    }

    /**
     * Creates an IPQueue primed with server addresses from the Tracker an array of last resort (if
     * necessairy)
     * 
     * @return a primed IPQueue
     */
    private IPQueue createPrimedIpQueue() {
        MysterServer[] iparray = IPListManagerSingleton.getIPListManager().getTop(type, 50);

        IPQueue queue = new IPQueue();

        int i = 0;

        for (i = 0; (i < iparray.length) && (iparray[i] != null); i++) {
            queue.addIP(iparray[i].getAddress());
        }

        if (i == 0) {
            String[] lastresort = IPListManagerSingleton.getIPListManager().getOnRamps();

            for (int j = 0; j < lastresort.length; j++) {
                try {
                    queue.addIP(new MysterAddress(lastresort[j]));
                    System.out.println("last resort: " + lastresort[j]);
                } catch (UnknownHostException ex) {
                    //..
                }
            }
        }
        return queue;
    }

    /**
     * Removes a Future from the list of oustanding Futures. Must be called from event thread.
     * 
     * @param future
     */
    private void removeFuture(Future future) {
        boolean success = outStandingFutures.remove(future);

        if (!success) {
            throw new IllegalStateException("!!!!!!Could not remove this future!!!!!!!!!!!!");
        }
        checkForDone();
    }

    /**
     * Checks to see if the search is done by checking for the numebr of outstanding futures.
     * <p>
     * Sets isDone to true if search is over. Also notifies other threads waiting on this search
     * using waitForEnd();
     *  
     */
    private void checkForDone() {
        if (outStandingFutures.size() == 0) {
            synchronized (this) {
                isDone = true;
                notifyAll();
                System.out.println("DONE search!");
            }
        }
    }

    /**
     * Adds search results to the search listener. This routine must be synchronized to make sure
     * that flagToEnd() hasen't been called (and returned) while still adding search results to the
     * listener. (The search cancelling routines of this object are thread safe)
     * 
     * @param mysterSearchResults
     */
    private synchronized void addResults(final MysterSearchResult[] mysterSearchResults) {
        if (endFlag)
            return;

        listener.addSearchResults(mysterSearchResults);
    }

    private final class UdpSearch extends CancellableCallableRemover {
        private final MysterAddress address;

        private UdpSearch(MysterAddress address) {
            super();
            this.address = address;
        }

        /**
         * This is called if there was no error
         */
        public void handleResult(Object result) {
            if (endFlag)
                return;

            final Vector results = (Vector) result;

            ++serversSearched;
            msg.say("Searched " + serversSearched + " servers...");

            if (results.size() == 0)
                return; // nothing to do, nowhere to go home.

            final MysterSearchResult[] mysterSearchResults = new MysterSearchResult[results.size()];

            for (int i = 0; i < results.size(); i++) {
                mysterSearchResults[i] = new MysterSearchResult(new MysterFileStub(address, type,
                        (String) (results.elementAt(i))));
            }

            addResults(mysterSearchResults);
            dealWithFileStats(address, mysterSearchResults);
        }

        public void handleException(Exception ex) {
            queueStreamSearch(address, type);
        }

    }

    private class UdpTopTen extends CancellableCallableRemover {
        private final IPQueue ipQueue;

        private UdpTopTen(IPQueue ipQueue) {
            super();
            this.ipQueue = ipQueue;
        }

        public void handleResult(Object result) {
            MysterAddress[] addresses = (MysterAddress[]) result;

            for (int i = 0; i < addresses.length; i++) {
                ipQueue.addIP(addresses[i]);
                IPListManagerSingleton.getIPListManager().addIP(addresses[i]);
            }

            processNewAddresses(ipQueue);
        }

        public void handleException(Exception ex) {
            processNewAddresses(ipQueue);
        }
    }

    public abstract class StreamSection implements CancellableCallable {
        protected MysterAddress address;

        protected MysterSocket socket = null;

        private StreamSection(MysterAddress address) {
            this.address = address;
        }

        /**
         * Instead of over-riding call, implementors of this class should over-ride doSection() as
         * we need to make sure the socket object is opened and closed correctly.
         */
        public final Object call() throws IOException {
            try {
                socket = MysterSocketFactory.makeStreamConnection(address);
                doSection();
            } catch (IOException ex) {
                //ignore
            } finally {
                try {
                    socket.close();
                } catch (Exception ex) {
                    //ignore
                }
            }

            return null;
        }

        /**
         * An over-ridden version of call() that doens't need to return anything (since stream
         * section return results via the listener in StandardMysterSearch.
         * 
         * @throws IOException
         */
        protected abstract void doSection() throws IOException;

        /**
         * Closes the socket. Subclasses should call this method using super if they over-ride this
         * method.
         */
        public void cancel() {
            if (socket == null)
                return;

            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }

    private class StreamSearch extends StreamSection {
        private MysterType type;

        private StreamSearch(MysterAddress address, MysterType type) {
            super(address);
            this.type = type;
        }

        public void doSection() throws IOException {
            searcher.search(socket, address, type);
        }
    }

    private class StreamFileStats extends StreamSection {
        private MysterSearchResult[] mysterSearchResults;

        public StreamFileStats(MysterAddress address, MysterSearchResult[] mysterSearchResults) {
            super(address);
            this.mysterSearchResults = mysterSearchResults;
        }

        public void doSection() throws IOException {
            searcher.dealWithFileStats(socket, type, mysterSearchResults, listener);

            Util.invoke(new Runnable() {
                public void run() {
                    if (endFlag)
                        return;
                    ++serversSearched;
                    msg.say("Searched " + serversSearched + " servers...");

                }
            });
        }
    }

    /**
     * Simple listener that makes sure that the Future is removed from the list of futures
     * maintained by this MysterSearch object.
     */
    private class CancellableCallableRemover implements CallListener {
        private Future future;

        public void handleCancel() {
        }

        public void handleResult(Object result) {
        }

        public void handleException(Exception ex) {
        }

        /**
         * Removed the Future form the lis tof futures.
         */
        public void handleFinally() {
            removeFuture(future);
        }

        /**
         * This methd should be called to assign a Future object to this object.
         * 
         * @param future
         */
        public void setFuture(Future future) {
            this.future = future;
        }
    }
}