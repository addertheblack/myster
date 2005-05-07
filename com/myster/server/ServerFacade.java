package com.myster.server;

import java.awt.Choice;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.TextField;
import java.util.StringTokenizer;

import com.myster.client.datagram.PongTransport;
import com.myster.client.datagram.UDPPingClient;
import com.myster.net.DatagramProtocolManager;
import com.myster.pref.Preferences;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.server.datagram.FileStatsDatagramServer;
import com.myster.server.datagram.PingTransport;
import com.myster.server.datagram.SearchDatagramServer;
import com.myster.server.datagram.SearchHashDatagramServer;
import com.myster.server.datagram.ServerStatsDatagramServer;
import com.myster.server.datagram.TopTenDatagramServer;
import com.myster.server.datagram.TypeDatagramServer;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.transaction.TransactionManager;
import com.myster.transferqueue.TransferQueue;

public class ServerFacade {
    private static Operator opp;

    private static boolean b = true;

    private static TransferQueue downloadQueue;
    
    private static ServerEventDispatcher serverDispatcher = new ServerEventDispatcher();

    /**
     * call this if you code assumes the server is actively running or you wish to start the server.
     * This routine should not be called by the user, only the system startup thread.
     */
    public static synchronized void assertServer() {
        if (b) {
            opp.start();
            b = false;
        }
    }

    public static void init() {
        BannersManager.init(); //init banners stuff..

        downloadQueue = new ServerQueue();
        downloadQueue.setMaxQueueLength(20);
        opp = new Operator(downloadQueue, getServerThreads(), getServerDispatcher());
        Preferences.getInstance().addPanel(new PrefPanel());
        addStandardStreamConnectionSections();
        TransactionManager.init(getServerDispatcher());
        initDatagramTransports();
        addStandardDatagramTransactions();
    }

    /**
     *  
     */
    private static void initDatagramTransports() {
        PongTransport ponger = new PongTransport();
        UDPPingClient.setPonger(ponger);
        DatagramProtocolManager.addTransport(ponger);
        DatagramProtocolManager.addTransport(new PingTransport());
    }

    /**
     *  
     */
    private static void addStandardDatagramTransactions() {
        TransactionManager.addTransactionProtocol(new TopTenDatagramServer());
        TransactionManager.addTransactionProtocol(new TypeDatagramServer());
        TransactionManager.addTransactionProtocol(new SearchDatagramServer());
        TransactionManager.addTransactionProtocol(new ServerStatsDatagramServer());
        TransactionManager.addTransactionProtocol(new FileStatsDatagramServer());
        TransactionManager.addTransactionProtocol(new SearchHashDatagramServer());
    }

    /**
     * Gets the server event dispatcher. Useful if you want your module to listen for SERVER events.
     */
    public static ServerEventDispatcher getServerDispatcher() {
        return serverDispatcher;
    }

    private static String identityKey = "ServerIdentityKey/";

    protected static void setIdentity(String s) {
        if (s == null)
            return;
        Preferences.getInstance().put(identityKey, s);
    }

    public static String getIdentity() {
        return Preferences.getInstance().query(identityKey);
    }

    public static void addConnectionSection(ConnectionSection section) {
        opp.addConnectionSection(section);
    }

    private static String serverThreadKey = "MysterTCPServerThreads/";

    private static int getServerThreads() {
        String info = Preferences.getInstance().get(serverThreadKey);
        if (info == null)
            info = "35"; //default value;
        try {
            return Integer.parseInt(info);
        } catch (NumberFormatException ex) {
            return 35; //should *NEVER* happen.
        }
    }

    private static void setServerThreads(int i) {
        Preferences.getInstance().put(serverThreadKey, "" + i);
    }

    private static void addStandardStreamConnectionSections() {
        opp.addConnectionSection(new com.myster.server.stream.IPLister());
        opp.addConnectionSection(new com.myster.server.stream.RequestDirThread());
        opp.addConnectionSection(new com.myster.server.stream.FileSenderThread());
        opp.addConnectionSection(new com.myster.server.stream.FileTypeLister());
        opp.addConnectionSection(new com.myster.server.stream.RequestSearchThread());
        opp.addConnectionSection(new com.myster.server.stream.HandshakeThread());
        opp.addConnectionSection(new com.myster.server.stream.FileInfoLister());
        opp.addConnectionSection(new com.myster.server.stream.FileByHash());
        opp.addConnectionSection(new com.myster.server.stream.MultiSourceSender());
        opp.addConnectionSection(new com.myster.server.stream.FileTypeListerII());
    }

    private static void setDownloadSpots(int spots) {
        downloadQueue.setDownloadSpots(spots);
    }

    private static int getDownloadSpots() {
        return downloadQueue.getDownloadSpots();
    }

    private static class PrefPanel extends PreferencesPanel {
        private final TextField serverIdentityField;

        private final Label serverIdentityLabel;

        private final Choice openSlotChoice;

        private final Label openSlotLabel;

        private final Choice serverThreadsChoice;

        private final Label serverThreadsLabel;

        private final Label spacerLabel;

        private final Label explanation;

        private final com.myster.server.stream.FileSenderThread.FreeLoaderPref leech;

        public PrefPanel() {
            //setBackground(Color.red);
            setLayout(new GridLayout(5, 2, 5, 5));

            openSlotLabel = new Label("Download Spots:");
            add(openSlotLabel);

            openSlotChoice = new Choice();
            for (int i = 2; i <= 10; i++) {
                openSlotChoice.add("" + i);
            }
            add(openSlotChoice);

            serverThreadsLabel = new Label("Server Threads: * (expert setting)");
            add(serverThreadsLabel);

            serverThreadsChoice = new Choice();
            serverThreadsChoice.add("" + 35);
            serverThreadsChoice.add("" + 40);
            serverThreadsChoice.add("" + 60);
            serverThreadsChoice.add("" + 80);
            serverThreadsChoice.add("" + 120);
            add(serverThreadsChoice);

            serverIdentityLabel = new Label("Server Identity:");
            add(serverIdentityLabel);

            serverIdentityField = new TextField();
            add(serverIdentityField);

            spacerLabel = new Label();
            add(spacerLabel);

            leech = com.myster.server.stream.FileSenderThread.getPrefPanel();
            add(leech);

            explanation = new Label("          * requires restart");
            add(explanation);

            reset();
        }

        public Dimension getPreferredSize() {
            return new Dimension(STD_XSIZE, 140);
        }

        public String getKey() {
            return "Server";
        }

        public void save() {
            ServerFacade.setIdentity(serverIdentityField.getText());
            ServerFacade.setDownloadSpots(Integer.parseInt(openSlotChoice.getSelectedItem()));
            setServerThreads(Integer.parseInt((new StringTokenizer(serverThreadsChoice
                    .getSelectedItem(), " ")).nextToken()));
            leech.save();
        }

        public void reset() {
            serverIdentityField.setText(ServerFacade.getIdentity());
            openSlotChoice.select("" + ServerFacade.getDownloadSpots());
            serverThreadsChoice.select("" + getServerThreads());
            leech.reset();
        }

    }
}