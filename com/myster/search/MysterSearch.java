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
import com.myster.util.MysterThread;
import com.myster.util.Sayable;
import com.sun.java.util.collections.Collections;
import com.sun.java.util.collections.HashSet;
import com.sun.java.util.collections.Iterator;
import com.sun.java.util.collections.Set;

public class MysterSearch extends MysterThread {
    private static final int MAX_CRAWLERS = 5;

    private Sayable msg;

    //private MysterThread t[];

    private MysterType type;

    private StandardMysterSearch searcher;

    private boolean endFlag = false;

    private boolean done = false; //we're done... start shutting down.

    private String searchString;

    private SearchResultListener listener;

    // private volatile int outstandingServices = 0;

    private volatile int serversSearched = 0;

    private volatile Set outStandingFutures;

    public MysterSearch(SearchResultListener listener, Sayable msg, MysterType type,
            String searchString) {
        this.msg = msg;
        this.searcher = new StandardMysterSearch(searchString, listener);
        this.type = type;
        this.searchString = searchString;
        this.listener = listener;

        outStandingFutures = new HashSet();
    }

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
     * This function must be called on the event thread.
     * 
     * @param address
     * @param ipQueue
     * @throws IOException
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
     * @param address
     * @param mysterSearchResults
     */
    private void dealWithFileStats(MysterAddress address, MysterSearchResult[] mysterSearchResults) {
        queueTcpSection(new TCPFileStats(address, mysterSearchResults));
    }

    private void queueTcpSection(final TCPSection section) {
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

    private void queueTcpSearch(final MysterAddress address, final MysterType type) {
        queueTcpSection(new TCPSearch(address, type));
    }

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
     * Must be called from event thread.
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

    private void checkForDone() {
        if (outStandingFutures.size() == 0) {
            synchronized (this) {
                done = true;
                notifyAll();
                System.out.println("DONE search!");
            }
        }
    }

    private synchronized void addResults(final MysterSearchResult[] mysterSearchResults) {
        if (endFlag)
            return;

        listener.addSearchResults(mysterSearchResults);
    }

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

    public void end() {
        msg.say("Stopping previous search threads (if any)..");
        //Stops all previous threads
        flagToEnd();

        waitForEnd();
    }

    public synchronized void waitForEnd() {
        if (done)
            return;

        try {
            while (!done) {
                wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
            queueTcpSearch(address, type);
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

    public abstract class TCPSection implements CancellableCallable {
        protected MysterAddress address;

        protected MysterSocket socket = null;

        private TCPSection(MysterAddress address) {
            this.address = address;
        }

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

        protected abstract void doSection() throws IOException;

        public void cancel() {
            if (socket == null)
                return;

            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }

    private class TCPSearch extends TCPSection {
        private MysterType type;

        private TCPSearch(MysterAddress address, MysterType type) {
            super(address);
            this.type = type;
        }

        public void doSection() throws IOException {
            searcher.search(socket, address, type);
        }
    }

    private class TCPFileStats extends TCPSection {
        private MysterSearchResult[] mysterSearchResults;

        public TCPFileStats(MysterAddress address, MysterSearchResult[] mysterSearchResults) {
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

    private class CancellableCallableRemover implements CallListener {
        private Future future;

        public void handleCancel() {
        }

        public void handleResult(Object result) {
        }

        public void handleException(Exception ex) {
        }

        public void handleFinally() {
            removeFuture(future);
        }

        public void setFuture(Future future) {
            this.future = future;
        }
    }
}