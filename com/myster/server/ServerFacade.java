package com.myster.server;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.general.util.DoubleBlockingQueue;
import com.myster.UpnpManager;
import com.myster.UpnpManager.HostAddress;
import com.myster.application.MysterGlobals;
import com.myster.client.datagram.PongTransport;
import com.myster.client.datagram.UDPPingClient;
import com.myster.client.net.MysterProtocol;
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
import com.myster.tracker.IPListManager;
import com.myster.transaction.TransactionManager;
import com.myster.transferqueue.TransferQueue;

public class ServerFacade {
    private static String identityKey = "ServerIdentityKey/";
    private static String serverThreadKey = "MysterTCPServerThreads/";

    private boolean b = true;

    private final Operator[] operators;
    private final TransferQueue transferQueue;
    private final ServerEventDispatcher serverDispatcher = new ServerEventDispatcher();
    private final DoubleBlockingQueue connectionQueue;
    private final ConnectionManager[] connectionManagers;
    private final Hashtable connectionSections = new Hashtable();
    private final IPListManager ipListManager;
    private final Preferences preferences;

    public ServerFacade(IPListManager ipListManager, Preferences preferences) {
        this.ipListManager = ipListManager;
        this.preferences = preferences;

        connectionManagers = new ConnectionManager[getServerThreads()];


        transferQueue = new ServerQueue();
        transferQueue.setMaxQueueLength(20);

        connectionQueue = new DoubleBlockingQueue(0);

        operators = new Operator[2];
        operators[0] = new Operator(connectionQueue, MysterGlobals.DEFAULT_PORT);
        operators[1] = new Operator(connectionQueue, 80); // .. arrrgghh

        addStandardStreamConnectionSections();
        TransactionManager.init(getServerDispatcher());
        initDatagramTransports();
        addStandardDatagramTransactions();

        InetAddress localHost = null;
        try {
            localHost = InetAddress.getLocalHost();
        } catch (UnknownHostException exception) {
            System.out.println("Could not punch upnp hole because localhost could not be found.");
            return;
        }

        List<HostAddress> hostAddresses = new ArrayList<>();
        for (Operator operator : operators) {
            hostAddresses.add(new HostAddress(localHost, operator.getPort()));
        }

        UpnpManager.initMapping(hostAddresses.toArray(new HostAddress[0]));
    }

    /**
     * call this if you code assumes the server is actively running or you wish
     * to start the server. This routine should not be called by the user, only
     * the system startup thread.
     */
    public synchronized void startServer() {
        if (b) {
            b = false;
            for (int i = 0; i < operators.length; i++) {
                operators[i].start();
            }

            for (int i = 0; i < connectionManagers.length; i++) {
                connectionManagers[i] = new ConnectionManager(connectionQueue,
                                                              serverDispatcher,
                                                              transferQueue,
                                                              connectionSections);
                connectionManagers[i].start();
            }
        }
    }

    /**
     * 
     */
    private static void initDatagramTransports() {
        PongTransport ponger = new PongTransport();
        DatagramProtocolManager.addTransport(ponger);
        DatagramProtocolManager.addTransport(new PingTransport());

        UDPPingClient.setPonger(ponger);
    }

    /**
     * 
     */
    private void addStandardDatagramTransactions() {
        TransactionManager.addTransactionProtocol(new TopTenDatagramServer(ipListManager));
        TransactionManager.addTransactionProtocol(new TypeDatagramServer());
        TransactionManager.addTransactionProtocol(new SearchDatagramServer());
        TransactionManager.addTransactionProtocol(new ServerStatsDatagramServer(this::getIdentity));
        TransactionManager.addTransactionProtocol(new FileStatsDatagramServer());
        TransactionManager.addTransactionProtocol(new SearchHashDatagramServer());
    }

    /**
     * Gets the server event dispatcher. Useful if you want your module to
     * listen for SERVER events.
     */
    public ServerEventDispatcher getServerDispatcher() {
        return serverDispatcher;
    }

    protected void setIdentity(String s) {
        if (s == null)
            return;
        preferences.put(identityKey, s);
    }

    public String getIdentity() {
        return preferences.query(identityKey);
    }

    public void addConnectionSection(ConnectionSection section) {
        connectionSections.put(section.getSectionNumber(), section);
    }

    private int getServerThreads() {
        String info = preferences.get(serverThreadKey);
        if (info == null)
            info = "35"; // default value;
        try {
            return Integer.parseInt(info);
        } catch (NumberFormatException ex) {
            return 35; // should *NEVER* happen.
        }
    }

    private void setServerThreads(int i) {
        preferences.put(serverThreadKey, "" + i);
    }

    private void addStandardStreamConnectionSections() {
        addConnectionSection(new com.myster.server.stream.IPLister(ipListManager));
        addConnectionSection(new com.myster.server.stream.RequestDirThread());
        addConnectionSection(new com.myster.server.stream.FileSenderThread());
        addConnectionSection(new com.myster.server.stream.FileTypeLister());
        addConnectionSection(new com.myster.server.stream.RequestSearchThread());
        addConnectionSection(new com.myster.server.stream.HandshakeThread(this::getIdentity));
        addConnectionSection(new com.myster.server.stream.FileInfoLister());
        addConnectionSection(new com.myster.server.stream.FileByHash());
        addConnectionSection(new com.myster.server.stream.MultiSourceSender());
        addConnectionSection(new com.myster.server.stream.FileTypeListerII());
    }

    private void setDownloadSpots(int spots) {
        transferQueue.setDownloadSpots(spots);
    }

    private int getDownloadSpots() {
        return transferQueue.getDownloadSpots();
    }

    public class ServerPrefPanel extends PreferencesPanel {
        private final JTextField serverIdentityField;

        private final JLabel serverIdentityLabel;

        private final JComboBox<String> openSlotChoice;

        private final JLabel openSlotLabel;

        private final JComboBox<String> serverThreadsChoice;

        private final JLabel serverThreadsLabel;

        private final JLabel spacerLabel;

        private final JLabel explanation;

        private final com.myster.server.stream.FileSenderThread.FreeLoaderPref leech;

        public ServerPrefPanel() {
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
            setIdentity(serverIdentityField.getText());
            setDownloadSpots(Integer.parseInt((String) openSlotChoice.getSelectedItem()));
            setServerThreads(Integer
                    .parseInt((new StringTokenizer((String) serverThreadsChoice.getSelectedItem(),
                                                   " ")).nextToken()));
            leech.save();
        }

        public void reset() {
            serverIdentityField.setText(getIdentity());
            openSlotChoice.setSelectedItem("" + getDownloadSpots());
            serverThreadsChoice.setSelectedItem("" + getServerThreads());
            leech.reset();
        }
    }
}