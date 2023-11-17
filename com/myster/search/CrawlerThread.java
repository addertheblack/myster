/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.search;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import java.util.function.Consumer;

import com.myster.client.stream.StandardSuite;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

/**
 * Does a crawl of the network calling "search" on the MysterSearchClientSection as it finds new ips
 * to crawl.
 */
public class CrawlerThread extends MysterThread {
    public static final int DEPTH = 20;
    
    private final Consumer<MysterAddress> addIp;
    private final MysterType searchType;
    private final IPQueue ipQueue;
    private final MysterSearchClientSection searcher;
    private final Sayable msg;

    private MysterSocket socket;
    private boolean endFlag = false;

    public CrawlerThread(MysterSearchClientSection searcher, MysterType type, IPQueue iplist,
            Sayable msg, Consumer<MysterAddress> addIp) {
        super("Crawler Thread " + type);
        this.addIp = addIp;
        this.ipQueue = iplist;
        this.searchType = type;
        this.msg = msg;
        this.searcher = searcher;
    }

    /**
     * The thread does a top ten then searches.. It only does a top ten on the first few IPs, so we
     * don't Do an insane flood.. This routine is responsible for most of the search.
     */

    public void run() {
        int counter = 0;

        //System.out.println("!CRAWLER THREAD Starting the crawl");

        try {

            for (MysterAddress currentIp = ipQueue.getNextIP(); currentIp != null || counter == 0; currentIp = ipQueue
                    .getNextIP()) {
                try {

                    counter++;
                    if (currentIp == null) {
                        try {
                            sleep(10 * 1000); //wait 10 seconds for more ips to
                            // come in.
                            continue;
                        } catch (InterruptedException ex) {
                            continue;
                        }
                    }

                    if (endFlag) {
                        cleanUp();
                        return;
                    }

                    socket = null;

                    if (endFlag) {
                        cleanUp();
                        return;
                    }

                    if (counter < DEPTH) {
                        List<MysterAddress> addresses = new ArrayList<>();
                        try {
                            String[] ipList = com.myster.client.datagram.StandardDatagramSuite
                                    .getTopServers(currentIp, searchType);
                            for (int i = 0; i < ipList.length; i++) {
                                try {
                                    addresses.add(new MysterAddress(ipList[i]));
                                } catch (UnknownHostException ignore) {
                                    // nothing
                                }
                            }
                        } catch (IOException ex) {
                            if (endFlag) {
                                cleanUp();
                                return;
                            }

                            socket = MysterSocketFactory.makeStreamConnection(currentIp);
                            List<String> ipList = StandardSuite.getTopServers(socket, searchType);

                            for (int i = 0; i < ipList.size(); i++) {
                                try {
                                    addresses
                                            .add(new MysterAddress(ipList.get(i)));
                                } catch (UnknownHostException ignore) {
                                    // ignore
                                }
                            }

                        }

                        if (endFlag) {
                            cleanUp();
                            return;
                        }

                        for (int i = 0; i < addresses.size(); i++) {
                            MysterAddress mysterAddress = addresses.get(i);
                            ipQueue.addIP(mysterAddress);
                            addIp.accept(mysterAddress);
                        }
                    }

                    if (endFlag) {
                        cleanUp();
                        return;
                    }

                    if (socket == null)
                        socket = MysterSocketFactory.makeStreamConnection(currentIp);
                    searcher.search(socket, currentIp, searchType);

                    StandardSuite.disconnectWithoutException(socket);
                } catch (IOException ex) {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException exp) {
                            // nothing
                            
                        }
                    }
                }

                msg.say("Searched " + ipQueue.getIndexNumber() + " Myster servers.");

            }

            searcher.searchedAll(searchType);
        } finally {
            searcher.endSearch(searchType);
        }

    }

    private void cleanUp() {

        try {
            socket.close();
        } catch (Exception ex) {
            // nothing
        }
    }

    public void end() {
        flagToEnd();

        try {
            join();
        } catch (InterruptedException ex) {
            // nothing
        }
    }

    public void flagToEnd() {
        endFlag = true;

        searcher.flagToEnd();

        try {
            socket.close();
        } catch (Exception ex) {
            System.out.println("Crawler thread was not happy about being asked to close.");
        }

        interrupt();

    }
}