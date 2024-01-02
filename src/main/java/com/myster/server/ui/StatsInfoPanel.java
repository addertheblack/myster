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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.general.util.LinkedList;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.server.event.ConnectionManagerEvent;
import com.myster.server.event.ConnectionManagerListener;
import com.myster.server.event.OperatorEvent;
import com.myster.server.event.OperatorListener;
import com.myster.server.event.ServerContext;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.event.ServerDownloadEvent;
import com.myster.server.event.ServerDownloadListener;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.server.event.ServerSearchListener;
import com.myster.server.stream.FileByHash;
import com.myster.server.stream.FileInfoLister;
import com.myster.server.stream.HandshakeThread;
import com.myster.server.stream.IpLister;
import com.myster.server.stream.MultiSourceSender;
import com.myster.server.stream.RequestSearchThread;

public class StatsInfoPanel extends JPanel {
    private JLabel numsearchlabel, listoflastten;

    private CountLabel numsearch;

    private JLabel searchperhourlabel;

    private CountLabel searchperhour;

    private XItemList lastten;

    private JLabel numofdllabel;

    private CountLabel numofld;

    private JLabel numofSSRLabel;

    private CountLabel numofSSR; // server stats

    private JLabel numofTTLabel;

    private CountLabel numofTT; // top ten

    private JLabel numofFILabel;

    private CountLabel numofFI; // files shared

    private JLabel numMatchesLabel;

    private CountLabel numMaches;

    private JLabel transferedLabel;

    private ByteCounter transfered;

    //
    private JLabel numberOfHashSearchesLabel;

    private CountLabel numberOfHashSearches;

    private JLabel numberOfConnectionsLabel;

    private CountLabel numberOfConnections;

    private JLabel currentConnectionsLabel;

    private CountLabel currentConnections;

    private JLabel numberOfPingsLabel;

    private CountLabel numberOfPings;

    private JLabel uptimeLabel;

    private JLabel uptime;

    private SearchPerHour searches = null;

    public StatsInfoPanel(ServerContext context) {

        setBackground(new Color(240, 240, 240));
        context.addConnectionManagerListener(new ConnectionHandler());

        // Load stuff
        init();

        context.addOperatorListener(new OperatorListener() {
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
        numsearchlabel = new JLabel("Number of Searches:");
        numsearchlabel.setSize(150, 25);
        numsearchlabel.setLocation(20, 30);
        add(numsearchlabel);

        numsearch = new CountLabel("0");
        numsearch.setSize(50, 25);
        numsearch.setLocation(200, 30);
        add(numsearch);

        listoflastten = new JLabel("Last Ten Search Strings");
        listoflastten.setSize(250, 25);
        listoflastten.setLocation(300, 30);
        add(listoflastten);

        lastten = new XItemList(10);
        JScrollPane scrollpane = new JScrollPane(lastten);
        scrollpane.setLocation(300, 60);
        add(scrollpane);
        scrollpane.setSize(300 - 20, 100);
        // lastten.runTester();

        searchperhourlabel = new JLabel("Searches in the last hour:");
        searchperhourlabel.setSize(150, 25);
        searchperhourlabel.setLocation(20, 60);
        add(searchperhourlabel);

        searchperhour = new CountLabel("0");
        searchperhour.setSize(50, 25);
        searchperhour.setLocation(200, 60);
        add(searchperhour);

        numMatchesLabel = new JLabel("Search Matches:");
        numMatchesLabel.setSize(150, 25);
        numMatchesLabel.setLocation(20, 90);
        add(numMatchesLabel);

        numMaches = new CountLabel("0");
        numMaches.setSize(50, 25);
        numMaches.setLocation(200, 90);
        add(numMaches);

        numofdllabel = new JLabel("Number Of Downloads:");
        numofdllabel.setSize(150, 25);
        numofdllabel.setLocation(20, 200);
        add(numofdllabel);

        numofld = new CountLabel("0");
        numofld.setSize(50, 25);
        numofld.setLocation(200, 200);
        add(numofld);

        numofTTLabel = new JLabel("Top Ten Requests:");
        numofTTLabel.setSize(175, 25);
        numofTTLabel.setLocation(20, 230);
        add(numofTTLabel);

        numofTT = new CountLabel("0");
        numofTT.setSize(50, 25);
        numofTT.setLocation(200, 230);
        add(numofTT);

        numofFILabel = new JLabel("File Info Requests:");
        numofFILabel.setSize(175, 25);
        numofFILabel.setLocation(20, 260);
        add(numofFILabel);

        numofFI = new CountLabel("0");
        numofFI.setSize(50, 25);
        numofFI.setLocation(200, 260);
        add(numofFI);

        numofSSRLabel = new JLabel("Server Stats Requests:");
        numofSSRLabel.setSize(175, 25);
        numofSSRLabel.setLocation(20, 290);
        add(numofSSRLabel);

        numofSSR = new CountLabel("0");
        numofSSR.setSize(50, 25);
        numofSSR.setLocation(200, 290);
        add(numofSSR);

        transferedLabel = new JLabel("Amount Transfered:");
        transferedLabel.setSize(175, 25);
        transferedLabel.setLocation(20, 320);
        add(transferedLabel);

        transfered = new ByteCounter();
        transfered.setValue(0);
        transfered.setSize(50, 25);
        transfered.setLocation(200, 320);
        add(transfered);

        numberOfHashSearchesLabel = new JLabel("Hash Look Ups:");
        numberOfHashSearchesLabel.setSize(175, 25);
        numberOfHashSearchesLabel.setLocation(300, 200);
        add(numberOfHashSearchesLabel);

        numberOfHashSearches = new CountLabel("0");
        numberOfHashSearches.setSize(50, 25);
        numberOfHashSearches.setLocation(500, 200);
        add(numberOfHashSearches);

        numberOfConnectionsLabel = new JLabel("Number Of Connections:");
        numberOfConnectionsLabel.setSize(175, 25);
        numberOfConnectionsLabel.setLocation(300, 230);
        add(numberOfConnectionsLabel);

        numberOfConnections = new CountLabel("0");
        numberOfConnections.setSize(50, 25);
        numberOfConnections.setLocation(500, 230);
        add(numberOfConnections);

        currentConnectionsLabel = new JLabel("Current Connections:");
        currentConnectionsLabel.setSize(175, 25);
        currentConnectionsLabel.setLocation(300, 260);
        add(currentConnectionsLabel);

        currentConnections = new CountLabel("0");
        currentConnections.setSize(50, 25);
        currentConnections.setLocation(500, 260);
        add(currentConnections);

        numberOfPingsLabel = new JLabel("Connections With No Requests:");
        numberOfPingsLabel.setSize(175, 25);
        numberOfPingsLabel.setLocation(300, 290);
        add(numberOfPingsLabel);

        numberOfPings = new CountLabel("0");
        numberOfPings.setSize(50, 25);
        numberOfPings.setLocation(500, 290);
        add(numberOfPings);

        uptimeLabel = new JLabel("Uptime:");
        uptimeLabel.setSize(175, 25);
        uptimeLabel.setLocation(300, 320);
        add(uptimeLabel);

        uptime = new JLabel("0");
        uptime.setSize(100, 25);
        uptime.setLocation(500, 320);
        add(uptime);
    }


    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        
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
            case MultiSourceSender.SECTION_NUMBER:
                numofld.increment(isUdp);
                ((ServerDownloadDispatcher) (e.getSectionObject()))
                        .addServerDownloadListener(new DownloadHandler());
                break;
            case IpLister.NUMBER:
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
        }

        public void sectionEventDisconnect(ConnectionManagerEvent e) {

        }
    }

    private class SearchHandler extends ServerSearchListener {

        public void searchRequested(ServerSearchEvent e) {
            lastten.add(e.getSearchString() + " (" + e.getType() + ")");
        }

        public void searchResult(ServerSearchEvent e) {
            numMaches.setValue(e.getResults().length + numMaches.getValue());
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
            uptime.setText(com.general.util.Util.getLongAsTime(System.currentTimeMillis()
                    - com.myster.application.MysterGlobals.getLaunchedTime()));
            if (!flag)
                return;
            new Timer(this, 5000);
        }

        public int calculateSearchesPerHour() {
            if (list.getTail() == null)
                return 0;
            while (1000 * 60 * 60 < (System.currentTimeMillis() - ((Long) (list.getTail()))
                    .longValue())) {
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

    private static class ByteCounter extends JLabel {
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