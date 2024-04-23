package com.myster.server;

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

import com.general.thread.BoundedExecutor;
import com.myster.application.MysterGlobals;
import com.myster.identity.Identity;
import com.myster.net.DatagramProtocolManager;
import com.myster.server.datagram.PingTransport;
import com.myster.server.event.ServerEventDispatcher;
import com.myster.server.transferqueue.ServerQueue;
import com.myster.server.transferqueue.TransferQueue;
import com.myster.tracker.MysterServerManager;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;

public class ServerFacade {
    private static final Logger LOGGER = Logger.getLogger(ServerFacade.class.getName());

    private boolean inited = true;

    private final Operator[] operators;
    private final TransferQueue transferQueue;
    private final ServerEventDispatcher serverDispatcher;
    private final Map<Integer, ConnectionSection> connectionSections = new HashMap<>();
    private final MysterServerManager ipListManager;
    private final ServerPreferences preferences;
    private final DatagramProtocolManager datagramManager;
    private final TransactionManager transactionManager;
    private final Executor operatorExecutor;
    private final Executor connectionExecutor;
    private final Identity identity;

    public ServerFacade(MysterServerManager ipListManager,
                        ServerPreferences preferences,
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

        transferQueue = new ServerQueue(preferences);
        
        Consumer<Socket> socketConsumer =
                (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                              serverDispatcher,
                                                                              transferQueue,
                                                                              connectionSections));

        final var operatorList = new ArrayList<Operator>();
        operatorList.add( new Operator(socketConsumer, preferences.getServerPort(), Optional.empty()));

        if (preferences.getServerPort() != MysterGlobals.DEFAULT_SERVER_PORT) {
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
        datagramManager.accessPort(preferences.getServerPort(), t -> t.addTransport(new PingTransport()));
    }

    public void addDatagramTransactions(TransactionProtocol ... protocols) {
        addDatagramTransactions(preferences.getServerPort(), protocols);
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
    
    public void addConnectionSection(ConnectionSection section) {
        connectionSections.put(section.getSectionNumber(), section);
    }

    private void addStandardStreamConnectionSections() {
        addConnectionSection(new com.myster.server.stream.IpLister(ipListManager));
        addConnectionSection(new com.myster.server.stream.RequestDirThread());
        addConnectionSection(new com.myster.server.stream.FileTypeLister());
        addConnectionSection(new com.myster.server.stream.RequestSearchThread());
        addConnectionSection(new com.myster.server.stream.ServerStats(preferences::getIdentityName, identity));
        addConnectionSection(new com.myster.server.stream.FileInfoLister());
        addConnectionSection(new com.myster.server.stream.FileByHash());
        addConnectionSection(new com.myster.server.stream.MultiSourceSender());
        addConnectionSection(new com.myster.server.stream.FileTypeListerII());
    }
}
