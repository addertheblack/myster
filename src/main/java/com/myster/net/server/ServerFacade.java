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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.general.thread.BoundedExecutor;
import com.myster.application.MysterGlobals;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
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

    private final Operator[] operators;
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
        
        this.operators = operatorList.toArray(Operator[]::new);
    }


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
        
        datagramManager.mutateTransportManager(MysterGlobals.DEFAULT_SERVER_PORT, t -> t.addTransport(new PingTransport(tracker)));
        addDatagramTransactions(MysterGlobals.DEFAULT_SERVER_PORT,
                                new ServerStatsDatagramServer(preferences::getIdentityName,
                                                              preferences::getServerPort,
                                                              identity,
                                                              fileManager));

        // Start mDNS service announcement (hybrid approach - doesn't replace
        // existing discovery)
        try {
            mdnsAnnouncer = new MysterMdnsAnnouncer(preferences.getIdentityName(),
                                                    preferences.getServerPort(),
                                                    identity);
            log.info("mDNS service announcement started");
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

            for (int i = 0; i < operators.length; i++) {
                operatorExecutor.execute(operators[i]);
            }
        }
    }

    public void addDatagramTransactions(TransactionProtocol ... protocols) {
        addDatagramTransactions(preferences.getServerPort(), protocols);
    }
    
    public void addDatagramTransactions(int port, TransactionProtocol ... protocols) {
        for (TransactionProtocol transactionProtocol : protocols) {
            transactionManager.addTransactionProtocol(port,transactionProtocol);
        }
    }

    public void addEncryptionSupport(DatagramEncryptUtil.Lookup serverLookup) {
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