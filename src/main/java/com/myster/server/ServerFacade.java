package com.myster.server;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.general.util.DoubleBlockingQueue;
import com.myster.application.MysterGlobals;
import com.myster.net.DatagramProtocolManager;
import com.myster.pref.MysterPreferences;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.server.datagram.PingTransport;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.tracker.IpListManager;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.transferqueue.TransferQueue;

public class ServerFacade {
    private static String IDENTITY_KEY = "ServerIdentityKey/";
    private static String serverThreadKey = "MysterTCPServerThreads/";

    private boolean b = true;

    private final Operator[] operators;
    private final TransferQueue transferQueue;
    private final ServerEventDispatcher serverDispatcher;
    private final DoubleBlockingQueue<Socket> connectionQueue;
    private final ConnectionManager[] connectionManagers;
    private final Map<Integer, ConnectionSection> connectionSections = new HashMap<>();
    private final IpListManager ipListManager;
    private final MysterPreferences preferences;
    private final DatagramProtocolManager datagramManager;
    private final TransactionManager transactionManager;

    public ServerFacade(IpListManager ipListManager,
                        MysterPreferences preferences,
                        DatagramProtocolManager datagramManager,
                        TransactionManager transactionManager,
                        ServerEventDispatcher serverDispatcher) {
        this.ipListManager = ipListManager;
        this.preferences = preferences;
        this.datagramManager = datagramManager;
        this.transactionManager = transactionManager;
        this.serverDispatcher = serverDispatcher;

        connectionManagers = new ConnectionManager[getServerThreads()];


        transferQueue = new ServerQueue();
        transferQueue.setMaxQueueLength(20);

        connectionQueue = new DoubleBlockingQueue<>(0);

        operators = new Operator[2];
        operators[0] = new Operator(connectionQueue, MysterGlobals.DEFAULT_PORT);
        operators[1] = new Operator(connectionQueue, 80); // .. arrrgghh

        addStandardStreamConnectionSections();
        initDatagramTransports();
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
    private void initDatagramTransports() {
        datagramManager.accessPort(MysterGlobals.DEFAULT_PORT, t -> t.addTransport(new PingTransport()));
    }

    /**
     * 
     */
    public void addDatagramTransactions(TransactionProtocol ... protocols) {
        for (TransactionProtocol transactionProtocol : protocols) {
            transactionManager.addTransactionProtocol(MysterGlobals.DEFAULT_PORT,transactionProtocol);
        }
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
        preferences.put(IDENTITY_KEY, s);
    }

    public String getIdentity() {
        return preferences.query(IDENTITY_KEY);
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
        addConnectionSection(new com.myster.server.stream.IpLister(ipListManager));
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