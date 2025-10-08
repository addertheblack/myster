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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.general.thread.CallListener;
import com.general.thread.Cancellable;
import com.general.thread.CancellableCallable;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;
import com.myster.net.MysterAddress;
import com.myster.net.MysterClientSocketPool;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.Sayable;

/**
 * Implements the Myster search algorithm using the protocol stack in StandardSuite and
 * StandardDatagramSuite.
 * <p>
 * This object is setup to synchronized all its activities using the event thread. All callback from
 * asynchronous functions arrive on the event thread. All interactions with the outside world with
 * internal data structures re also done on the event thread.
 * <p>
 * Implementation notes:
 * <p>
 * The idea here is that just about everything done by this code is input/output across an un-
 * reliable internet connection. This mean that just about everything can block. This blocking
 * problem is handled by doing everything via callbacks. That is, you request something from the
 * protocol stack and it will call back your "CallListener" on the event thread. This pattern is
 * used for the whole search engine so all we do in this class is request that a certain type of IO
 * be done then wait for Myster's protocol framework to call us back.. When we get the callback we
 * can take appropriate action depending on whether we managed to get the data, the remote ip wasn't
 * responding etc...Since everything is done via callbacks it can make following the program flow
 * fairly difficult. The starting point is the "run()" method, which just starts off the
 * asynchronous tasks.
 */
public class MysterSearch {
    private static final Logger LOGGER = Logger.getLogger(MysterSearch.class.getName());
    
    /** Contains the object to pass status messages to. */
    private final Sayable msg;

    /** Contains the type to search on. */
    private MysterType type;

    /** Contains the stream based searcher algorithm code. */
    private final StandardMysterSearch searcher;

    /**
     * Is set to true if some one has requested that this search be stopped.
     * <p>
     * <b>NOTE: The is a delay between when the search is requested to be stopped and when the
     * search actually stops. That is the "endFlag" and the isDone flag are not set at the same
     * time. <b>
     */
    private boolean endFlag = false;

    /**
     * Is set to true when there are no more searches left outstanding (when the search is over).
     */
    private boolean isDone = false; //we're done... start shutting down.

    /** Contains the search string being used to search. */
    private String searchString;

    /** Contains the search result event listener for this search. */
    private final SearchResultListener listener;

    /** Contains the number of servers that have been SUCCESSFULLY searched. */
    private volatile int serversSearched = 0;

    /**
     * Contains Future objects for each asynchronous call still outstanding. Future Objects
     * represent outstanding asynchronous tasks. If this Set is empty then we're done since there's
     * nothing left pending.
     */
    private final Set<Cancellable> outStandingFutures;
    private final Tracker tracker;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashManager;
    private final MysterFrameContext context;

    public MysterSearch(MysterProtocol protocol, 
                        HashCrawlerManager hashManager,
                        MysterFrameContext context, 
                        Tracker tracker,
                        SearchResultListener listener,
                        Sayable msg,
                        MysterType type,
                        String searchString) {
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.context = context;
        this.msg = (String s) -> Util.invokeNowOrLater(() -> msg.say(s));
        this.searcher = new StandardMysterSearch(protocol,
                                                 hashManager,
                                                 context,
                                                 tracker,
                                                 searchString,
                                                 type,
                                                 listener);
        this.type = type;
        this.searchString = searchString;
        this.listener = listener;
        this.tracker = tracker;

        outStandingFutures = new HashSet<>();
    }

    /**
     * Call this routine to start the search off. The search is completely asynchronous so this
     * routine returns immediately; there's no point in putting in a thread object. The search
     * notifies listeners of important events during the search.
     * <p>
     * This function can be called from any thread.
     */
    public void run() {
        msg.say("SEARCH: Starting Search..");

        /*
         * Since we are using the event thread to synchronized we need to put all code that touches
         * state onto the event thread. (We don't know which thread is calling us!)
         */
        Util.invokeLater(new Runnable() {
            public void run() {
                listener.searchStart();

                IPQueue queue = createPrimedIpQueue();
                processNewAddresses(queue);

                /*
                 * if this is not here and we are cancelled too early then no one will call
                 * removeFuture to trigger a checkForDone(). We can check here because we are
                 * assured that there should be some futures available because they were just
                 * created above. If they haven't been created then we were cancelled before we
                 * could create any and should therefor end NOW.
                 */
                checkForDone();
            }
        });
    }

    /**
     * Call this routine to request the file search to end. While there is a lag between when this
     * command is issued and when the searching actually stops (due to some technical issues) this
     * object guarantees that the listener will not receiving any new search results or search
     * result stats updates after this function returns.
     * <p>
     * Can be called on any thread.
     */
    public void flagToEnd() {
        synchronized (this) {
            if (isDone)
                return; //don't cancel if done. This is not a speed hack.
            endFlag = true;
        }

        searcher.flagToEnd();

        Util.invokeLater(new Runnable() {
            public void run() {
                for (Cancellable cancellable : outStandingFutures) {
                    cancellable.cancel();
                }
                listener.searchOver();
            }
        });
    }

    /**
     * This routine cancels the search and blocks until the search is completely over (until
     * isDone() returns true). This blocking could theoretically last up to 90 seconds (because we
     * must wait for connection attempts to time out)!
     * <p>
     * Can be calle don any thread.
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
     * <p>
     * Can be called on any thread. CANNOT be called on event thread!
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
     * <p>
     * Can be called on any thread.
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
     * <p>
     * Can be called on any thread.
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

        // ADD_TOP_TEN
        UdpTopTen  udpTopTenListener = new UdpTopTen(ipQueue);
        PromiseFuture<String[]> topTenFuture = protocol.getDatagram().getTopServers(new ParamBuilder(address), type).addCallListener(udpTopTenListener);
        udpTopTenListener.setFuture(topTenFuture);
        outStandingFutures.add(topTenFuture);

        // ADD_SEARCH
        UdpSearch udpSearchListener = new UdpSearch(address);
        PromiseFuture<List<String>> searchFuture = protocol.getDatagram().getSearch(new ParamBuilder(address), type, searchString).addCallListener(udpSearchListener);
        udpSearchListener.setFuture(searchFuture);
        outStandingFutures.add(searchFuture);
    }

    /**
     * Starts off the process for dealing with file stats information for a list
     * of files. Currently this is done using a streamed connection, however in
     * future the datagram version could be used.
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
        Util.invokeLater(new Runnable() {
            public void run() {
                if (endFlag)
                    return;
                CancellableCallableRemover<Object> remover = new CancellableCallableRemover<Object>();
                PromiseFuture<Object> future = PromiseFutures.execute(section, MysterClientSocketPool.getExecutorInstance()).addCallListener(remover).useEdt();
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
     */
    private void queueStreamSearch(final MysterAddress address) {
        queueStreamSection(new StreamSearch(address));
    }

    /**
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
     * necessary).
     * <p>
     * NOTE: has a side effect of possibly creating tasks to lookup various hostnames from the array
     * of last resort asynchronously and "processing" them!
     * 
     * @return a primed IPQueue
     */
    private IPQueue createPrimedIpQueue() {
        MysterServer[] iparray = tracker.getTop(type, 50);

        IPQueue queue = new IPQueue();

        int i = 0;

        for (i = 0; (i < iparray.length) && (iparray[i] != null); i++) {
            iparray[i].getBestAddress().ifPresent(queue::addIP);
        }

        if (i <= 4) {
            String[] lastresort = Tracker.getOnRamps();

            for (int j = 0; j < lastresort.length; j++) {
                addAddressToQueue(queue, lastresort[j]);
            }
        }
        
        return queue;
    }

    /**
     * Since converting a string address to a MysterAddress *might* cause some IO, we need to do it
     * asynchronously. This code does all the hard work of adding/removing Futures and putting the
     * code to create the MysterAddress on a pooled thread.
     * <p>
     * Since this code run asynchronously, the queue will not contain the address. What will happen
     * is the address will be turned into a MysterAddress on a different thread then the new
     * MysterAddress will be added to the queue an then the queue processed for new addresses.
     * 
     * @param queue
     * @param address
     */
    private void addAddressToQueue(final IPQueue queue, final String address) {
        CancellableCallable<MysterAddress> addressLookup =
                new CancellableCallable<MysterAddress>() {
                    public MysterAddress call() throws Exception {
                        return MysterAddress.createMysterAddress(address);
                    }

            public void cancel() {
                // nothing
            }
        };

        CancellableCallableRemover<MysterAddress> callListener = new CancellableCallableRemover<MysterAddress>() {
            public void handleResult(MysterAddress result) {
                queue.addIP( result);
                processNewAddresses(queue);
            }
        };
        PromiseFuture<MysterAddress> future = PromiseFutures
                .execute(addressLookup, MysterClientSocketPool.getExecutorInstance()).addCallListener(callListener)
                .useEdt();
        callListener.setFuture(future);
        outStandingFutures.add(future);
    }

    /**
     * Removes a Future from the list of outstanding Futures. Must be called from event thread.
     * 
     * @param future
     */
    private void removeFuture(Cancellable future) {
        if (future == null) {
            throw new NullPointerException("future is null");
        }
        
        boolean success = outStandingFutures.remove(future);

        if (!success) {
            throw new IllegalStateException("!!!!!!Could not remove this future!!!!!!!!!!!!");
        }
        checkForDone();
    }

    /**
     * Checks to see if the search is done by checking for the number of outstanding futures.
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
                LOGGER.info("DONE search!");

                if (endFlag)
                    return; // don't call searchOver() if end flag is set (it
                // has already been called).
            }
            Util.invokeNowOrLater(() -> {
                if (endFlag)
                    return;
                
                listener.searchOver();
            });
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
        Util.invokeNowOrLater(() -> {
            if (endFlag)
                return;

            listener.addSearchResults(mysterSearchResults);
        });
    }

    private final class UdpSearch extends CancellableCallableRemover<List<String>> {
        private final MysterAddress address;

        private UdpSearch(MysterAddress address) {
            super();
            this.address = address;
        }

        /**
         * This is called if there was no error
         */
        public void handleResult(List<String> results) {
            if (endFlag)
                return;

            ++serversSearched;
            msg.say("Searched " + serversSearched + " servers...");

            if (results.size() == 0)
                return; // nothing to do, nowhere to go home.

            final MysterSearchResult[] mysterSearchResults = new MysterSearchResult[results.size()];

            for (int i = 0; i < results.size(); i++) {
                mysterSearchResults[i] =
                        new MysterSearchResult(protocol,
                                               hashManager,
                                               context,
                                               new MysterFileStub(address, type, results.get(i)),
                                               tracker::getQuickServerStats);
            }

            addResults(mysterSearchResults);
            dealWithFileStats(address, mysterSearchResults);
        }

        public void handleException(Throwable ex) {
            queueStreamSearch(address);
        }

    }

    private class UdpTopTen extends CancellableCallableRemover<String[]> {
        private final IPQueue ipQueue;

        private UdpTopTen(IPQueue ipQueue) {
            super();
            this.ipQueue = ipQueue;
        }

        public void handleResult(String[] addresses) {

            /*
             * We go through the list of returned addresses and first try to make a MysterAddress
             * without doing a host lookup then, if it can't, send it off to do a host lookup
             * asynchronously.
             */
            for (int i = 0; i < addresses.length; i++) {
                try {
                    MysterAddress mysterAddress = MysterAddress.createMysterAddress(addresses[i]);
                    ipQueue.addIP(mysterAddress);
                    tracker.addIp(mysterAddress);
                } catch (UnknownHostException exception) {
                    addAddressToQueue(ipQueue, addresses[i]);
                }
            }

            processNewAddresses(ipQueue);
        }

        @Override
        public void handleException(Throwable ex) {
            processNewAddresses(ipQueue);
        }
    }

    public abstract class StreamSection implements CancellableCallable<Object> {
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
                // nothing
            }
        }
    }

    private class StreamSearch extends StreamSection {
        private StreamSearch(MysterAddress address) {
            super(address);
        }

        public void doSection() throws IOException {
            searcher.search(socket, address);

            Util.invokeLater(new Runnable() {
                public void run() {
                    if (endFlag)
                        return;
                    ++serversSearched;
                    msg.say("Searched " + serversSearched + " servers...");

                }
            });
        }
    }

    private class StreamFileStats extends StreamSection {
        private MysterSearchResult[] mysterSearchResults;

        public StreamFileStats(MysterAddress address, MysterSearchResult[] mysterSearchResults) {
            super(address);
            this.mysterSearchResults = mysterSearchResults;
        }

        public void doSection() throws IOException {
            searcher.dealWithFileStats(socket, mysterSearchResults);
        }
    }

    /**
     * Simple listener that makes sure that the Future is removed from the list
     * of futures maintained by this MysterSearch object.
     */
    private class CancellableCallableRemover<T> implements CallListener<T> {
        private PromiseFuture<T> future;

        public void handleCancel() {
            // nothing
        }

        public void handleResult(T result) {
            // nothing
        }

        public void handleException(Throwable ex) {
            // nothing
        }

        /**
         * Removed the Future form the list of futures.
         */
        public void handleFinally() {
            removeFuture(future);
        }

        /**
         * This method should be called to assign a Future object to this object.
         * 
         * @param future
         */
        public void setFuture(PromiseFuture<T>  future) {
            this.future = future;
        }
    }
}
