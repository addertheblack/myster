package com.myster.net.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.general.thread.BoundedExecutor;
import com.myster.application.MysterGlobals;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.net.datagram.DatagramProtocolManager;
import com.myster.net.mdns.MysterMdnsAnnouncer;
import com.myster.net.server.datagram.PingTransport;
import com.myster.net.server.datagram.ServerStatsDatagramServer;
import com.myster.net.stream.server.transferqueue.ServerQueue;
import com.myster.net.stream.server.transferqueue.TransferQueue;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.tracker.Tracker;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;

public class ServerFacade {
    private static final Logger log = Logger.getLogger(ServerFacade.class.getName());

    private boolean inited = true;

    private List<Operator> operators;
    private final TransferQueue transferQueue;
    private final ServerEventDispatcher serverDispatcher;
    private final Map<Integer, ConnectionSection> connectionSections = new HashMap<>();
    private final Tracker tracker;
    private final ServerPreferences preferences;
    private final DatagramProtocolManager datagramManager;
    private final TransactionManager transactionManager;
    private final Executor operatorExecutor;
    private final Executor connectionExecutor;
    private final Identity identity;
    private final FileTypeListManager fileManager;
    private MysterMdnsAnnouncer mdnsAnnouncer; // mDNS service announcer (optional)

    // Track protocols added to main port so we can move them when port changes
    private final List<TransactionProtocol> mainPortProtocols = new CopyOnWriteArrayList<>();
    private DatagramEncryptUtil.Lookup encryptionLookup;

    public ServerFacade(Tracker tracker,
                        ServerPreferences preferences,
                        DatagramProtocolManager datagramManager,
                        TransactionManager transactionManager,
                        Identity identity,
                        FileTypeListManager fileManager,
                        ServerEventDispatcher serverDispatcher) {
        this.tracker = tracker;
        this.preferences = preferences;
        this.datagramManager = datagramManager;
        this.transactionManager = transactionManager;
        this.identity = identity;
        this.fileManager = fileManager;
        this.serverDispatcher = serverDispatcher;
        this.operatorExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.connectionExecutor = new BoundedExecutor(120, operatorExecutor);
        
        transferQueue = new ServerQueue(preferences);
        
        Consumer<Socket> socketConsumer =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              identity,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              fileManager,
                                                                              connectionSections));
        final var operatorList = new ArrayList<Operator>();
        operatorList.add( new Operator(socketConsumer, preferences.getServerPort(), Optional.empty()));
        
        
        if (preferences.getServerPort() != MysterGlobals.DEFAULT_SERVER_PORT) {
            log.fine("Initializing LAN operator");
            try {
                initLanResourceDiscovery(operatorList);
            } catch (SocketException exception) {
                log.log(Level.WARNING, "Could not initialize LAN socket", exception);
            }
        }
        
        initMDns();
        
        this.operators = operatorList;
    }


    /**
     * Initializes LAN resource discovery on DEFAULT_SERVER_PORT.
     * This creates operators bound to LAN addresses only for local discovery,
     * and adds PingTransport and ServerStatsDatagramServer for UDP-based discovery.
     * <p>
     * This method is idempotent - it will remove any existing LAN discovery protocols
     * before adding new ones, so it's safe to call during port changes.
     */
    public void initLanResourceDiscovery(List<Operator> operatorList)
            throws  SocketException {
        Consumer<Socket> serviceDiscoveryPort =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              identity,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              fileManager,
                                                                              new HashMap<>()));
        List<InetAddress> publicLandAddresses = ServerUtils.findMyLanAddress();
        for (InetAddress publicLandAddress : publicLandAddresses) {
            operatorList.add(new Operator(serviceDiscoveryPort,
                                          MysterGlobals.DEFAULT_SERVER_PORT,
                                          Optional.of( publicLandAddress)));
        }
        
        // Remove existing LAN discovery protocols if present (makes this idempotent for port changes)
        datagramManager.mutateTransportManager(MysterGlobals.DEFAULT_SERVER_PORT, t -> {
            t.removeTransport(DatagramConstants.PING_TRANSPORT_CODE);
            t.addTransport(new PingTransport(tracker));
            return null;
        });

        // Remove existing ServerStats if present, then add new one
        transactionManager.removeTransactionProtocol(MysterGlobals.DEFAULT_SERVER_PORT,
                DatagramConstants.SERVER_STATS_TRANSACTION_CODE);
        addDatagramTransactions(MysterGlobals.DEFAULT_SERVER_PORT,
                                new ServerStatsDatagramServer(preferences::getIdentityName,
                                                              preferences::getServerPort,
                                                              identity,
                                                              fileManager));

    }


    /**
     * Initializes mDNS service announcement for LAN discovery.
     * Always starts mDNS announcing the configured server port, regardless of
     * whether we're on the default port or not. LAN clients need to discover
     * us via mDNS no matter what port we're running on.
     */
    private void initMDns() {
        try {
            mdnsAnnouncer = new MysterMdnsAnnouncer(preferences.getIdentityName(),
                                                    preferences.getServerPort(),
                                                    identity);
            log.info("mDNS service announcement started for port " + preferences.getServerPort());
        } catch (IOException e) {
            log.warning("Failed to start mDNS announcement (continuing without it): "
                    + e.getMessage());
            // Continue anyway - mDNS is optional, existing UDP discovery
            // still works
        }
    }


    /**
     * call this if you code assumes the server is actively running or you wish
     * to start the server. This routine should not be called by the user, only
     * the system startup thread.
     */
    public synchronized void startServer() {
        if (inited) {
            inited = false;

            for (Operator operator : operators) {
                operatorExecutor.execute(operator);
            }
        }
    }

    /**
     * Changes the server port dynamically. Stops old operators and creates new ones
     * on the new port. Existing connections on the old port will continue to be
     * processed until they complete naturally - they are not forcibly disconnected.
     * <p>
     * This method handles the transition between port configurations:
     * <ul>
     *   <li>DEFAULT → Non-Default: Creates main operator on new port + LAN operators on DEFAULT</li>
     *   <li>Non-Default → DEFAULT: Creates single main operator on DEFAULT, no LAN operators needed</li>
     *   <li>Non-Default X → Non-Default Y: Swaps main operator, LAN operators are recreated</li>
     * </ul>
     * <p>
     * Also moves datagram transaction protocols and encryption support to the new port.
     * mDNS is restarted to announce the new port. UPnP mappings should be updated
     * by the caller after this method returns.
     *
     * @param oldPort the previous port (needed because preferences are already updated when this is called)
     * @param newPort the new port to listen on
     */
    public synchronized void changePort(int oldPort, int newPort) {
        if (oldPort == newPort) {
            log.info("Port unchanged, nothing to do");
            return;
        }

        boolean goingToDefault = (newPort == MysterGlobals.DEFAULT_SERVER_PORT);

        log.info("Changing server port from " + oldPort + " to " + newPort);

        // Stop old operators - they will drain naturally
        for (Operator operator : operators) {
            operator.flagToEnd();
        }

        // Create new operator list
        List<Operator> newOperators = new ArrayList<>();

        // Main operator on new port with full connection sections
        Consumer<Socket> socketConsumer =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              identity,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              fileManager,
                                                                              connectionSections));
        newOperators.add(new Operator(socketConsumer, newPort, Optional.empty()));

        // LAN operators if not on default port
        if (!goingToDefault) {
            try {
                initLanResourceDiscovery(newOperators);
            } catch (SocketException e) {
                log.log(Level.WARNING, "Could not initialize LAN socket during port change", e);
            }
        }

        // Start new operators
        for (Operator operator : newOperators) {
            operatorExecutor.execute(operator);
        }

        operators = newOperators;

        // Move datagram transaction protocols to new port
        moveDatagramProtocolsToNewPort(oldPort, newPort);

        // Restart mDNS with new port
        shutdownMdns();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        initMDns();

        log.info("Server port changed to " + newPort);
    }

    /**
     * Moves the datagram transaction protocols and encryption support from old port to new port.
     * Removes protocols from old port and adds them to new port.
     * <p>
     * Handles edge cases:
     * <ul>
     *   <li>6669 → 7000: Remove main protocols from 6669, add to 7000. LAN discovery will add its own to 6669.</li>
     *   <li>7000 → 6669: Remove main protocols from 7000, remove LAN protocols from 6669, add main protocols to 6669.</li>
     *   <li>7000 → 8000: Remove main protocols from 7000, add to 8000. LAN protocols on 6669 are recreated by initLanResourceDiscovery.</li>
     * </ul>
     */
    private void moveDatagramProtocolsToNewPort(int oldPort, int newPort) {
        boolean wasOnDefault = (oldPort == MysterGlobals.DEFAULT_SERVER_PORT);
        boolean goingToDefault = (newPort == MysterGlobals.DEFAULT_SERVER_PORT);

        // If going TO default port, we need to clean up the LAN discovery protocols that were there
        // because we'll be putting the main protocols there instead
        if (goingToDefault && !wasOnDefault) {
            // Remove LAN discovery protocols from DEFAULT_PORT (they were added by initLanResourceDiscovery)
            datagramManager.mutateTransportManager(MysterGlobals.DEFAULT_SERVER_PORT, t -> {
                t.removeTransport(DatagramConstants.PING_TRANSPORT_CODE);
                return null;
            });
            // ServerStatsDatagramServer is a TransactionProtocol, remove it too
            transactionManager.removeTransactionProtocol(MysterGlobals.DEFAULT_SERVER_PORT,
                    DatagramConstants.SERVER_STATS_TRANSACTION_CODE);
        }

        if (!wasOnDefault) {
            datagramManager.mutateTransportManager(oldPort, t -> {
                t.removeTransport(DatagramConstants.PING_TRANSPORT_CODE);
                return null;
            });
        }

        // Add PingTransport to new port
        datagramManager.mutateTransportManager(newPort, t -> {
            // Remove if already exists (shouldn't happen but be safe)
            t.removeTransport(DatagramConstants.PING_TRANSPORT_CODE);
            t.addTransport(new PingTransport(tracker));
            return null;
        });

        // Move all tracked transaction protocols from old port to new port
        for (TransactionProtocol protocol : mainPortProtocols) {
            transactionManager.removeTransactionProtocol(oldPort, protocol.getTransactionCode());
            transactionManager.addTransactionProtocol(newPort, protocol);
        }

        // Move encryption support to new port
        if (encryptionLookup != null) {
            // EncryptedDatagramServer uses STLS_CODE as its transaction code
            transactionManager.removeTransactionProtocol(oldPort, DatagramConstants.STLS_CODE);
            transactionManager.addEncryptionSupport(newPort, encryptionLookup);
        }

        log.info("Moved " + mainPortProtocols.size() + " datagram protocols from port " + oldPort + " to port " + newPort);
    }

    /**
     * Adds transaction protocols to the server's main port.
     * These protocols will be tracked and moved if the port is changed.
     */
    public void addDatagramTransactions(TransactionProtocol ... protocols) {
        addDatagramTransactions(preferences.getServerPort(), protocols);
        // Track protocols added to the main port
        for (TransactionProtocol protocol : protocols) {
            mainPortProtocols.add(protocol);
        }
    }

    /**
     * Adds transaction protocols to a specific port.
     * Note: Protocols added to non-main ports are NOT tracked for port changes.
     */
    public void addDatagramTransactions(int port, TransactionProtocol ... protocols) {
        for (TransactionProtocol transactionProtocol : protocols) {
            transactionManager.addTransactionProtocol(port, transactionProtocol);
        }
    }

    /**
     * Adds encryption support to the server's main port.
     * This will be tracked and moved if the port is changed.
     */
    public void addEncryptionSupport(DatagramEncryptUtil.Lookup serverLookup) {
        this.encryptionLookup = serverLookup;
        transactionManager.addEncryptionSupport(preferences.getServerPort(), serverLookup);
    }

    /**
     * Gets the server event dispatcher. Useful if you want your module to
     * listen for SERVER events.
     */
    public ServerEventDispatcher getServerDispatcher() {
        return serverDispatcher;
    }
    
    public void addConnectionSection(ConnectionSection section) {
        connectionSections.put(section.getSectionNumber(), section);
    }
    
    /**
     * Shuts down the mDNS service announcer if it was started.
     * Should be called when the server is shutting down.
     */
    public void shutdownMdns() {
        if (mdnsAnnouncer != null) {
            mdnsAnnouncer.close();
            log.info("mDNS announcer shut down");
        }
    }
    
    /**
     * Updates the mDNS service name when the server identity changes.
     * 
     * @param newServerName the new server name
     */
    public void updateMdnsServerName(String newServerName) {
        if (mdnsAnnouncer != null) {
            mdnsAnnouncer.updateServerName(newServerName);
        }
    }
}



