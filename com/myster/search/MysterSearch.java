/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
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

import com.general.util.BlockingQueue;
import com.myster.client.datagram.StandardDatagramSuite;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.net.StandardDatagramEvent;
import com.myster.net.StandardDatagramListener;
import com.myster.tracker.IPListManager;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.tracker.MysterServer;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

public class MysterSearch extends MysterThread {
    private static final int MAX_CRAWLERS = 5;

    private Sayable msg;

    private MysterThread t[];

    private MysterType type;

    private StandardMysterSearch searcher;

    private boolean endFlag = false;

    private boolean done = false; //we're done... start shutting down.

    private String searchString;

    private SearchResultListener listener;

    private volatile int tempTopTenCounter = 0;

    private volatile int serversSearched = 0;

    public MysterSearch(SearchResultListener listener, Sayable msg, MysterType type,
            String searchString) {
        this.msg = msg;
        this.searcher = new StandardMysterSearch(searchString, listener);
        this.type = type;
        this.searchString = searchString;
        this.listener = listener;

        t = new MysterThread[MAX_CRAWLERS];
    }

    public void run() {
        msg.say("SEARCH: Starting Search..");

        searcher.start();

        final BlockingQueue blockingQueue = new BlockingQueue(); // a queue of
        // runnable
        // objects to
        // be executed
        // by the pool!

        IPQueue queue = createPrimedIpQueue();

        processNewAddresses(queue, blockingQueue);

        for (int i = 0; i < t.length; i++) {
            t[i] = new PoolableMysterSearchThread(blockingQueue);//new
            // CrawlerThread(searcher,
            // type, queue,
            // msg);
            t[i].start();
            msg.say("Starting a new Search Thread...");
        }

        for (int index = 0; index < t.length; index++) {
            try {
                t[index].join();
            } catch (InterruptedException ex) {
            } // slow: change someday.
        }
    }

    private void searchThisIp(final MysterAddress address, final IPQueue ipQueue,
            final BlockingQueue blockingQueue) throws IOException {
        if (endFlag)
            return;

        StandardDatagramSuite.getTopServers(address, type, new StandardDatagramListener() {
            /**
             * This is called if there was no error
             */
            public void response(StandardDatagramEvent event) { // not blocking
                MysterAddress[] addresses = (MysterAddress[]) event.getData();

                for (int i = 0; i < addresses.length; i++) {
                    ipQueue.addIP(addresses[i]);
                    IPListManagerSingleton.getIPListManager().addIP(addresses[i]);
                }

                processNewAddresses(ipQueue, blockingQueue);

                dyingTopTenUdp();
            }

            /**
             * This is called if there was a timeout
             */
            public void timeout(StandardDatagramEvent event) {
                processNewAddresses(ipQueue, blockingQueue);

                dyingTopTenUdp();
            }

            /**
             * This is called if there was a negative response (This can be
             * assume to mean the protocol was not understood)
             */
            public void error(StandardDatagramEvent event) {
                processNewAddresses(ipQueue, blockingQueue);

                dyingTopTenUdp();
            }
        });

        StandardDatagramSuite.getSearch(address, type, searchString,
                new StandardDatagramListener() { // not blocking..
                    /**
                     * This is called if there was no error
                     */
                    public void response(StandardDatagramEvent event) {
                        if (endFlag)
                            return;

                        final Vector results = (Vector) event.getData();

                        ++serversSearched;
                        msg.say("Searched " + serversSearched + " servers...");

                        if (results.size() == 0)
                            return; // nothing to do, nowhere to go home.

                        final MysterSearchResult[] mysterSearchResults = new MysterSearchResult[results
                                .size()];

                        for (int i = 0; i < results.size(); i++) {
                            mysterSearchResults[i] = new MysterSearchResult(new MysterFileStub(
                                    address, type, (String) (results.elementAt(i))));
                        }

                        listener.addSearchResults(mysterSearchResults);

                        blockingQueue.add(new MysterThread() {
                            public void run() {
                                MysterSocket socket = null;
                                try {
                                    socket = MysterSocketFactory.makeStreamConnection(address);

                                    searcher.dealWithFileStats(socket, type, mysterSearchResults,
                                            listener);
                                } catch (IOException ex) {
                                    // humm..
                                } finally {
                                    try {
                                        socket.close();
                                    } catch (Exception ex) {
                                        // don't care
                                    }
                                }
                            }
                        });

                    }

                    /**
                     * This is called if there was a timeout
                     */
                    public void timeout(StandardDatagramEvent event) {
                        queueTcpSearch(blockingQueue, address, type); // that
                        // didn't
                        // work..
                        // try
                        // TCP...
                    }

                    /**
                     * This is called if there was a negative response (This can
                     * be assume to mean the protocol was not understood)
                     */
                    public void error(StandardDatagramEvent event) {
                        queueTcpSearch(blockingQueue, address, type);
                    }
                });
    }

    private void dyingTopTenUdp() {
        --tempTopTenCounter;

        System.out.println("Top Ten completed. Only " + tempTopTenCounter + " left.");

        if (tempTopTenCounter == 0)
            done = true;
    }

    private void queueTcpSearch(BlockingQueue queue, final MysterAddress address,
            final MysterType type) {

        queue.add(new MysterThread() {
            public void run() {
                MysterSocket socket = null;

                try {
                    socket = MysterSocketFactory.makeStreamConnection(address);
                    System.out.println("Searching using TCP");
                    searcher.search(socket, address, type);
                } catch (IOException ex) {
                    // humm.. server must be crap.. ignore..
                } finally {
                    try {
                        socket.close();
                    } catch (Exception ex) {
                        //ignore
                    }

                    ++serversSearched;
                    msg.say("Searched " + serversSearched + " servers...");
                }
            }
        });
    }

    private void processNewAddresses(final IPQueue ipQueue, final BlockingQueue blockingQueue) {
        for (;;) {
            MysterAddress address = ipQueue.getNextIP();

            if (address == null)
                return; // humm, end of da line... no point in
            // going on.

            try {
                searchThisIp(address, ipQueue, blockingQueue);
                ++tempTopTenCounter;
            } catch (IOException ex) {
                // humm.. an exception.. Is UDP down?
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

    public void flagToEnd() {
        for (int i = 0; i < IPListManager.LISTSIZE; i++) {
            try {
                t[i].flagToEnd();
            } catch (Exception ex) {
            } // slow: change someday. lol, yeah right..
        }

        searcher.flagToEnd();
    }

    public void end() {
        msg.say("Stopping previous search threads (if any)..");
        //Stops all previous threads
        flagToEnd();
        try {
            join();
        } catch (Exception ex) {
        }
    }

}

class PoolableMysterSearchThread extends MysterThread {
    private boolean endFlag = false;

    private BlockingQueue queue;

    private MysterThread currentThread;

    /**
     * @param queue
     */
    public PoolableMysterSearchThread(BlockingQueue queue) {
        super();
        this.queue = queue;
    }

    public void run() {
        MysterThread current = null;
        for (;;) {
            try {
                try {
                    current = (MysterThread) queue.get();
                } catch (InterruptedException ex) {
                    return;
                }

                synchronized (this) {
                    if (endFlag)
                        return;
                    currentThread = current;
                }

                System.out.println("Running a thread form pool. + " + queue.length());
                current.run();

                synchronized (this) {
                    if (endFlag)
                        return;
                    currentThread = null;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public synchronized void flagToEnd() {
        endFlag = true;

        if (currentThread != null)
            currentThread.flagToEnd();

        this.interrupt();
    }

    public void end() {
        flagToEnd();
        try {
            this.join();
        } catch (InterruptedException ex) {
        }
    }
}