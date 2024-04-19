package com.myster.server;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.general.thread.BoundedExecutor;
import com.myster.application.MysterGlobals;
import com.myster.identity.Identity;
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
    private static final Logger LOGGER = Logger.getLogger(ServerFacade.class.getName());
    private static String IDENTITY_NAME_KEY = "ServerIdentityKey/";

    private boolean inited = true;

    private final Operator[] operators;
    private final TransferQueue transferQueue;
    private final ServerEventDispatcher serverDispatcher;
    private final Map<Integer, ConnectionSection> connectionSections = new HashMap<>();
    private final IpListManager ipListManager;
    private final MysterPreferences preferences;
    private final DatagramProtocolManager datagramManager;
    private final TransactionManager transactionManager;
    private final Executor operatorExecutor;
    private final Executor connectionExecutor;
    private final Identity identity;

    public ServerFacade(IpListManager ipListManager,
                        MysterPreferences preferences,
                        DatagramProtocolManager datagramManager,
                        TransactionManager transactionManager,
                        Identity identity,
                        ServerEventDispatcher serverDispatcher) {
        this.ipListManager = ipListManager;
        this.preferences = preferences;
        this.datagramManager = datagramManager;
        this.transactionManager = transactionManager;
        this.identity = identity;
        this.serverDispatcher = serverDispatcher;
        this.operatorExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.connectionExecutor = new BoundedExecutor(120, operatorExecutor);

        transferQueue = new ServerQueue();
        transferQueue.setMaxQueueLength(20);
        
        Consumer<Socket> socketConsumer =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              connectionSections));

        final var operatorList = new ArrayList<Operator>();
        operatorList.add( new Operator(socketConsumer, getServerPort(), Optional.empty()));

        if (getServerPort() != MysterGlobals.DEFAULT_SERVER_PORT) {
            LOGGER.fine("Initializing LAN operator");
            try {
                initLanResourceDiscovery(serverDispatcher, operatorList);
            } catch (UnknownHostException exception) {
                LOGGER.log(Level.WARNING, "Could not initialize LAN socket", exception);
            }
        }
        
        this.operators = operatorList.toArray(new Operator[0]);

        addStandardStreamConnectionSections();
        initDatagramTransports();
    }


    private void initLanResourceDiscovery(ServerEventDispatcher serverDispatcher,
                                          ArrayList<Operator> operatorList)
            throws UnknownHostException {
        Consumer<Socket> serviceDiscoveryPort =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              new HashMap<>()));
        List<InetAddress> publicLandAddresses = ServerUtils.findPublicLandAddress();
        for (InetAddress publicLandAddress : publicLandAddresses) {
            operatorList.add(new Operator(serviceDiscoveryPort,
                                          MysterGlobals.DEFAULT_SERVER_PORT,
                                          Optional.of( publicLandAddress)));
        }
        
        datagramManager.accessPort(MysterGlobals.DEFAULT_SERVER_PORT, t -> t.addTransport(new PingTransport()));
    }


    /**
     * call this if you code assumes the server is actively running or you wish
     * to start the server. This routine should not be called by the user, only
     * the system startup thread.
     */
    public synchronized void startServer() {
        if (inited) {
            inited = false;

            for (int i = 0; i < operators.length; i++) {
                operatorExecutor.execute(operators[i]);
            }
        }
    }

    private void initDatagramTransports() {
        datagramManager.accessPort(getServerPort(), t -> t.addTransport(new PingTransport()));
    }

    public void addDatagramTransactions(TransactionProtocol ... protocols) {
        addDatagramTransactions(getServerPort(), protocols);
    }
    public void addDatagramTransactions(int port, TransactionProtocol ... protocols) {
        for (TransactionProtocol transactionProtocol : protocols) {
            transactionManager.addTransactionProtocol(port,transactionProtocol);
        }
    }

    /**
     * Gets the server event dispatcher. Useful if you want your module to
     * listen for SERVER events.
     */
    public ServerEventDispatcher getServerDispatcher() {
        return serverDispatcher;
    }

    protected void setIdentityName(String s) {
        if (s == null)
            return;
        preferences.put(IDENTITY_NAME_KEY, s);
    }

    public String getIdentityName() {
        return preferences.query(IDENTITY_NAME_KEY);
    }
    
    public void addConnectionSection(ConnectionSection section) {
        connectionSections.put(section.getSectionNumber(), section);
    }

    private Integer getServerPort() {
        return preferences.getInt("Server Port", MysterGlobals.DEFAULT_SERVER_PORT);
    }
    
    private void setPort(int value) {
        preferences.putInt("Server Port", value);
    }

    private void addStandardStreamConnectionSections() {
        addConnectionSection(new com.myster.server.stream.IpLister(ipListManager));
        addConnectionSection(new com.myster.server.stream.RequestDirThread());
        addConnectionSection(new com.myster.server.stream.FileTypeLister());
        addConnectionSection(new com.myster.server.stream.RequestSearchThread());
        addConnectionSection(new com.myster.server.stream.ServerStats(this::getIdentityName, identity));
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


    public static class FreeLoaderPref extends JPanel {
        private final JCheckBox freeloaderCheckbox;

        public FreeLoaderPref() {
            setLayout(new FlowLayout());

            freeloaderCheckbox = new JCheckBox("Kick Freeloaders");
            add(freeloaderCheckbox);
        }

        public void save() {
            setKickFreeloaders(freeloaderCheckbox.isSelected());
        }

        public void reset() {
            freeloaderCheckbox.setSelected(kickFreeloaders());
        }

        public Dimension getPreferredSize() {
            return new Dimension(100, 1);
        }
        

        private static String freeloadKey = "ServerFreeloaderKey/";

        public static boolean kickFreeloaders() {
            boolean b_temp = false;

            try {
                b_temp = Boolean
                        .valueOf(MysterPreferences.getInstance().get(freeloadKey))
                        .booleanValue();
            } catch (NumberFormatException ex) {
                //nothing
            } catch (NullPointerException ex) {
                //nothing
            }
            return b_temp;
        }

        private static void setKickFreeloaders(boolean b) {
            MysterPreferences.getInstance().put(freeloadKey, "" + b);
        }
    }
    
    public class ServerPrefPanel extends PreferencesPanel {
        private final JTextField serverIdentityField;
        private final JLabel serverIdentityLabel;
        private final JComboBox<String> openSlotChoice;
        private final JLabel openSlotLabel;
        private final JSpinner serverThreadsChoice;
        private final JLabel serverThreadsLabel;
        private final JLabel spacerLabel;

        private final FreeLoaderPref leech;

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

            serverThreadsLabel = new JLabel("Server Port:");
            add(serverThreadsLabel);

            var spinnerNumberModel = new SpinnerNumberModel();
            spinnerNumberModel.setMinimum(1024);
            spinnerNumberModel.setMaximum((int)Math.pow(2, 16) - 1);
            spinnerNumberModel.setValue(getServerPort());
            serverThreadsChoice = new JSpinner(spinnerNumberModel);
            ((JSpinner.DefaultEditor) serverThreadsChoice.getEditor()).getTextField().setEditable(true);
            add(serverThreadsChoice);

            serverIdentityLabel = new JLabel("Server Name:");
            add(serverIdentityLabel);

            serverIdentityField = new JTextField();
            add(serverIdentityField);

            spacerLabel = new JLabel();
            add(spacerLabel);

            leech = new FreeLoaderPref();
            add(leech);

            reset();
        }

        public Dimension getPreferredSize() {
            return new Dimension(STD_XSIZE, 140);
        }

        public String getKey() {
            return "Server";
        }

        public void save() {
            setIdentityName(serverIdentityField.getText());
            setDownloadSpots(Integer.parseInt((String) openSlotChoice.getSelectedItem()));
            setPort((int) serverThreadsChoice.getModel().getValue());
            leech.save();
        }

        public void reset() {
            serverIdentityField.setText(getIdentityName());
            openSlotChoice.setSelectedItem("" + getDownloadSpots());
            serverThreadsChoice.getModel().setValue(getServerPort());
            leech.reset();
        }
    }
}