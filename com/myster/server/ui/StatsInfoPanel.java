/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.myster.server.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;

import com.general.util.LinkedList;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.server.ServerFacade;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.ConnectionManagerListener;
import com.myster.server.event.OperatorEvent;
import com.myster.server.event.OperatorListener;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.event.ServerDownloadEvent;
import com.myster.server.event.ServerDownloadListener;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.server.event.ServerSearchListener;
import com.myster.server.stream.FileByHash;
import com.myster.server.stream.FileInfoLister;
import com.myster.server.stream.FileSenderThread;
import com.myster.server.stream.HandshakeThread;
import com.myster.server.stream.IPLister;
import com.myster.server.stream.MultiSourceSender;
import com.myster.server.stream.RequestSearchThread;

public class StatsInfoPanel extends Panel {
    Label numsearchlabel, listoflastten;

    CountLabel numsearch;

    Label searchperhourlabel;

    CountLabel searchperhour;

    XItemList lastten;

    Label numofdllabel;

    CountLabel numofld;

    Label numofSSRLabel;

    CountLabel numofSSR; //server stats

    Label numofTTLabel;

    CountLabel numofTT; //top ten

    Label numofFILabel;

    CountLabel numofFI; //files shared

    Label numMatchesLabel;

    CountLabel numMaches;

    Label transferedLabel;

    ByteCounter transfered;

    //
    Label numberOfHashSearchesLabel;

    CountLabel numberOfHashSearches;

    Label numberOfConnectionsLabel;

    CountLabel numberOfConnections;

    Label currentConnectionsLabel;

    CountLabel currentConnections;

    Label numberOfPingsLabel;

    CountLabel numberOfPings;

    Label uptimeLabel;

    Label uptime;

    ServerEventDispatcher server;

    SearchPerHour searches = null;

    public StatsInfoPanel() {

        setBackground(new Color(240, 240, 240));
        server = ServerFacade.getServerDispatcher();
        server.addConnectionManagerListener(new ConnectionHandler());

        //Load stuff
        init();

        server.addOperatorListener(new OperatorListener() {
            public void pingEvent(OperatorEvent e) {
                numberOfPings.increment(false);
            }

            public void disconnectEvent(OperatorEvent e) {
                currentConnections.decrement(false);
            }

            public void connectEvent(OperatorEvent e) {
                currentConnections.increment(false);
                numberOfConnections.increment(false);
            }
        });

        searches = new SearchPerHour();
        searches.start();
    }

    private void init() {
        setLayout(null);
        numsearchlabel = new Label("Number of Searches:");
        numsearchlabel.setSize(150, 25);
        numsearchlabel.setLocation(20, 30);
        add(numsearchlabel);

        numsearch = new CountLabel("0");
        numsearch.setSize(50, 25);
        numsearch.setLocation(200, 30);
        add(numsearch);

        listoflastten = new Label("Last Ten Search Strings");
        listoflastten.setSize(250, 25);
        listoflastten.setLocation(300, 30);
        add(listoflastten);

        lastten = new XItemList(10);
        lastten.setLocation(300, 60);
        add(lastten);
        lastten.setSize(300 - 20, 100);
        //lastten.runTester();

        searchperhourlabel = new Label("Searches in the last hour:");
        searchperhourlabel.setSize(150, 25);
        searchperhourlabel.setLocation(20, 60);
        add(searchperhourlabel);

        searchperhour = new CountLabel("0");
        searchperhour.setSize(50, 25);
        searchperhour.setLocation(200, 60);
        add(searchperhour);

        numMatchesLabel = new Label("Search Matches:");
        numMatchesLabel.setSize(150, 25);
        numMatchesLabel.setLocation(20, 90);
        add(numMatchesLabel);

        numMaches = new CountLabel("0");
        numMaches.setSize(50, 25);
        numMaches.setLocation(200, 90);
        add(numMaches);

        numofdllabel = new Label("Number Of Downloads:");
        numofdllabel.setSize(150, 25);
        numofdllabel.setLocation(20, 200);
        add(numofdllabel);

        numofld = new CountLabel("0");
        numofld.setSize(50, 25);
        numofld.setLocation(200, 200);
        add(numofld);

        numofTTLabel = new Label("Top Ten Requests:");
        numofTTLabel.setSize(175, 25);
        numofTTLabel.setLocation(20, 230);
        add(numofTTLabel);

        numofTT = new CountLabel("0");
        numofTT.setSize(50, 25);
        numofTT.setLocation(200, 230);
        add(numofTT);

        numofFILabel = new Label("File Info Requests:");
        numofFILabel.setSize(175, 25);
        numofFILabel.setLocation(20, 260);
        add(numofFILabel);

        numofFI = new CountLabel("0");
        numofFI.setSize(50, 25);
        numofFI.setLocation(200, 260);
        add(numofFI);

        numofSSRLabel = new Label("Server Stats Requests:");
        numofSSRLabel.setSize(175, 25);
        numofSSRLabel.setLocation(20, 290);
        add(numofSSRLabel);

        numofSSR = new CountLabel("0");
        numofSSR.setSize(50, 25);
        numofSSR.setLocation(200, 290);
        add(numofSSR);

        transferedLabel = new Label("Amount Transfered:");
        transferedLabel.setSize(175, 25);
        transferedLabel.setLocation(20, 320);
        add(transferedLabel);

        transfered = new ByteCounter();
        transfered.setValue(0);
        transfered.setSize(50, 25);
        transfered.setLocation(200, 320);
        add(transfered);

        numberOfHashSearchesLabel = new Label("Hash Look Ups:");
        numberOfHashSearchesLabel.setSize(175, 25);
        numberOfHashSearchesLabel.setLocation(300, 200);
        add(numberOfHashSearchesLabel);

        numberOfHashSearches = new CountLabel("0");
        numberOfHashSearches.setSize(50, 25);
        numberOfHashSearches.setLocation(500, 200);
        add(numberOfHashSearches);

        numberOfConnectionsLabel = new Label("Number Of Connections:");
        numberOfConnectionsLabel.setSize(175, 25);
        numberOfConnectionsLabel.setLocation(300, 230);
        add(numberOfConnectionsLabel);

        numberOfConnections = new CountLabel("0");
        numberOfConnections.setSize(50, 25);
        numberOfConnections.setLocation(500, 230);
        add(numberOfConnections);

        currentConnectionsLabel = new Label("Current Connections:");
        currentConnectionsLabel.setSize(175, 25);
        currentConnectionsLabel.setLocation(300, 260);
        add(currentConnectionsLabel);

        currentConnections = new CountLabel("0");
        currentConnections.setSize(50, 25);
        currentConnections.setLocation(500, 260);
        add(currentConnections);

        numberOfPingsLabel = new Label("Connections With No Requests:");
        numberOfPingsLabel.setSize(175, 25);
        numberOfPingsLabel.setLocation(300, 290);
        add(numberOfPingsLabel);

        numberOfPings = new CountLabel("0");
        numberOfPings.setSize(50, 25);
        numberOfPings.setLocation(500, 290);
        add(numberOfPings);

        uptimeLabel = new Label("Uptime:");
        uptimeLabel.setSize(175, 25);
        uptimeLabel.setLocation(300, 320);
        add(uptimeLabel);

        uptime = new Label("0");
        uptime.setSize(100, 25);
        uptime.setLocation(500, 320);
        add(uptime);
    }

    private Image doubleBuffer; //adds double buffering

    public void update(Graphics g) {
        if (doubleBuffer == null) {
            doubleBuffer = createImage(600, 400);
        }
        Graphics graphics = doubleBuffer.getGraphics();
        paint(graphics);
        g.drawImage(doubleBuffer, 0, 0, this);
    }

    public void paint(Graphics g) {
        FontMetrics metrics = getFontMetrics(getFont());

        String msg1 = "Search Stats";

        g.setColor(new Color(150, 150, 150));
        g.drawRect(10, 10, 580, 170);
        g.setColor(getBackground());
        g.fillRect(15, 9, metrics.stringWidth(msg1) + 10, 2);

        g.setColor(getForeground());
        g.drawString(msg1, 20, 15);

    }

    private class ConnectionHandler extends ConnectionManagerListener {
        public void sectionEventConnect(ConnectionManagerEvent e) {
            boolean isUdp = e.isDatagram();
            switch (e.getSection()) {
            case RequestSearchThread.NUMBER:
                numsearch.increment(isUdp);
                searches.addSearch(e.getTimeStamp());
                ((ServerSearchDispatcher) (e.getSectionObject()))
                        .addServerSearchListener(new SearchHandler());
                break;
            case FileSenderThread.NUMBER:
            case MultiSourceSender.SECTION_NUMBER:
                numofld.increment(isUdp);
                ((ServerDownloadDispatcher) (e.getSectionObject()))
                        .addServerDownloadListener(new DownloadHandler());
                break;
            case IPLister.NUMBER:
                numofTT.increment(isUdp);
                break;
            case HandshakeThread.NUMBER:
                numofSSR.increment(isUdp);
                break;
            case FileInfoLister.NUMBER:
                numofFI.increment(isUdp);
                break;
            case FileByHash.NUMBER:
                numberOfHashSearches.increment(isUdp);
                break;
            }

            /*
             * if (e.getSection()==RequestSearchThread.NUMBER) {
             * numsearch.increment(); searches.addSearch(e.getTimeStamp());
             * ((ServerSearchDispatcher)(e.getSectionObject())).addServerSearchListener(new
             * SearchHandler()); } else if
             * ((e.getSection()==FileSenderThread.NUMBER) ||
             * (e.getSection()==com.myster.server.stream.MultiSourceSender.SECTION_NUMBER)) {
             * numofld.increment();
             * ((ServerDownloadDispatcher)(e.getSectionObject())).addServerDownloadListener(new
             * DownloadHandler()); } else if (e.getSection()==IPLister.NUMBER) {
             * numofTT.increment(); } else if
             * (e.getSection()==HandshakeThread.NUMBER) { numofSSR.increment(); }
             * else if (e.getSection()==FileInfoLister.NUMBER) {
             * numofFI.increment(); } else if
             * (e.getSection()==FileByHash.NUMBER) {
             * numberOfHashSearches.increment(); }
             */

        }

        public void sectionEventDisconnect(ConnectionManagerEvent e) {

        }
    }

    private class SearchHandler extends ServerSearchListener {

        public void searchRequested(ServerSearchEvent e) {
            lastten.add(e.getSearchString() + " (" + e.getType() + ")");
        }

        public void searchResult(ServerSearchEvent e) {
            numMaches.setValue(e.getResults().length+numMaches.getValue());
        }
    }

    private class SearchPerHour implements Runnable {
        boolean flag = true;

        LinkedList list = new LinkedList();

        public void start() {
            run();
        }

        public void run() {
            searchperhour.setValue(calculateSearchesPerHour(), false);
            uptime.setText(com.myster.Myster.getUptimeAsString(System
                    .currentTimeMillis()
                    - com.myster.Myster.getLaunchedTime()));
            if (!flag)
                return;
            Timer timer = new Timer(this, 5000);
        }

        public int calculateSearchesPerHour() {
            if (list.getTail() == null)
                return 0;
            while (1000 * 60 * 60 < (System.currentTimeMillis() - ((Long) (list
                    .getTail())).longValue())) {
                list.removeFromTail();
                if (list.getTail() == null)
                    return 0;
            }
            return list.getSize();
        }

        public void addSearch(long time) {
            list.addToHead(new Long(time));
        }

        public void end() {
            flag = false;
        }
    }

    private class DownloadHandler extends ServerDownloadListener {
        public void downloadFinished(ServerDownloadEvent e) {
            transfered.setValue(transfered.getValue() + e.dataSoFar()
                    - e.getDownloadInfo().getInititalOffset());
        }
    }

    private static class ByteCounter extends Label {
        long value = 0;

        public long getValue() {
            return value;
        }

        public synchronized void setValue(long i) {
            value = i;
            setUpdateLabel();
        }

        private synchronized void setUpdateLabel() {
            setText(Util.getStringFromBytes(value));
        }
    }
}