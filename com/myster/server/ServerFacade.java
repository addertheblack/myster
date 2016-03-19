package com.myster.server;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Hashtable;
import java.util.StringTokenizer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.general.util.DoubleBlockingQueue;
import com.myster.application.MysterGlobals;
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
    private static Operator[] operators;

    private static boolean b = true;

    private static TransferQueue transferQueue;

    private static ServerEventDispatcher serverDispatcher = new ServerEventDispatcher();

    private static DoubleBlockingQueue connectionQueue;

    private static ConnectionManager[] connectionManagers;

    private static Hashtable connectionSections = new Hashtable();

    /**
     * call this if you code assumes the server is actively running or you wish
     * to start the server. This routine should not be called by the user, only
     * the system startup thread.
     */
    public static synchronized void assertServer() {
        if (b) {
            b = false;
            for (int i = 0; i < operators.length; i++) {
                operators[i].start();
            }
            
            connectionManagers = new ConnectionManager[getServerThreads()];
            for (int i = 0; i < connectionManagers.length; i++) {
                connectionManagers[i] = new ConnectionManager(connectionQueue, serverDispatcher,
                        transferQueue, connectionSections);
                connectionManagers[i].start();
            }
        }
    }

    public static void init() {
        BannersManager.init(); // init banners stuff..

        transferQueue = new ServerQueue();
        transferQueue.setMaxQueueLength(20);
        connectionQueue = new DoubleBlockingQueue(0);
        operators = new Operator[2];
        operators[0] = new Operator(connectionQueue, MysterGlobals.DEFAULT_PORT);
        operators[1] = new Operator(connectionQueue, 80);
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
     * Gets the server event dispatcher. Useful if you want your module to
     * listen for SERVER events.
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
        connectionSections.put(new Integer(section.getSectionNumber()), section);
    }

    private static String serverThreadKey = "MysterTCPServerThreads/";

    private static int getServerThreads() {
        String info = Preferences.getInstance().get(serverThreadKey);
        if (info == null)
            info = "35"; // default value;
        try {
            return Integer.parseInt(info);
        } catch (NumberFormatException ex) {
            return 35; // should *NEVER* happen.
        }
    }

    private static void setServerThreads(int i) {
        Preferences.getInstance().put(serverThreadKey, "" + i);
    }

    private static void addStandardStreamConnectionSections() {
        addConnectionSection(new com.myster.server.stream.IPLister());
        addConnectionSection(new com.myster.server.stream.RequestDirThread());
        addConnectionSection(new com.myster.server.stream.FileSenderThread());
        addConnectionSection(new com.myster.server.stream.FileTypeLister());
        addConnectionSection(new com.myster.server.stream.RequestSearchThread());
        addConnectionSection(new com.myster.server.stream.HandshakeThread());
        addConnectionSection(new com.myster.server.stream.FileInfoLister());
        addConnectionSection(new com.myster.server.stream.FileByHash());
        addConnectionSection(new com.myster.server.stream.MultiSourceSender());
        addConnectionSection(new com.myster.server.stream.FileTypeListerII());
    }

    private static void setDownloadSpots(int spots) {
        transferQueue.setDownloadSpots(spots);
    }

    private static int getDownloadSpots() {
        return transferQueue.getDownloadSpots();
    }

    private static class PrefPanel extends PreferencesPanel {
        private final JTextField serverIdentityField;

        private final JLabel serverIdentityLabel;

        private final JComboBox<String> openSlotChoice;

        private final JLabel openSlotLabel;

        private final JComboBox<String> serverThreadsChoice;

        private final JLabel serverThreadsLabel;

        private final JLabel spacerLabel;

        private final JLabel explanation;

        private final com.myster.server.stream.FileSenderThread.FreeLoaderPref leech;

        public PrefPanel() {
            // setBackground(Color.red);
            setLayout(new GridLayout(5, 2, 5, 5));

            openSlotLabel = new JLabel("Download Spots:");
            add(openSlotLabel);

            openSlotChoice = new JComboBox<String>();
            for (int i = 2; i <= 10; i++) {
                openSlotChoice.addItem("" + i);
            }
            add(openSlotChoice);

            serverThreadsLabel = new JLabel("Server Threads: * (expert setting)");
            add(serverThreadsLabel);

            serverThreadsChoice = new JComboBox<String>();
            serverThreadsChoice.addItem("" + 35);
            serverThreadsChoice.addItem("" + 40);
            serverThreadsChoice.addItem("" + 60);
            serverThreadsChoice.addItem("" + 80);
            serverThreadsChoice.addItem("" + 120);
            add(serverThreadsChoice);

            serverIdentityLabel = new JLabel("Server Identity:");
            add(serverIdentityLabel);

            serverIdentityField = new JTextField();
            add(serverIdentityField);

            spacerLabel = new JLabel();
            add(spacerLabel);

            leech = com.myster.server.stream.FileSenderThread.getPrefPanel();
            add(leech);

            explanation = new JLabel("          * requires restart");
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
            ServerFacade
                    .setDownloadSpots(Integer.parseInt((String) openSlotChoice.getSelectedItem()));
            setServerThreads(Integer
                    .parseInt((new StringTokenizer((String) serverThreadsChoice.getSelectedItem(),
                                                   " ")).nextToken()));
            leech.save();
        }

        public void reset() {
            serverIdentityField.setText(ServerFacade.getIdentity());
            openSlotChoice.setSelectedItem("" + ServerFacade.getDownloadSpots());
            serverThreadsChoice.setSelectedItem("" + getServerThreads());
            leech.reset();
        }

    }
}