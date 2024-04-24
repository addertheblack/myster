
package com.myster.tracker;

import static com.myster.tracker.MysterServerImplementation.computeNodeNameFromIdentity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.server.stream.ServerStats;

/**
 * Given a string address, it returns a com.myster object. com.myster
 * objects are like little statistics objects. You can get these objects and
 * use these objects from anywhere in the program thanks to the new garbage
 * collector based system.
 */
public class MysterServerPoolImpl implements MysterServerPool {
    private static final Logger LOGGER = Logger.getLogger(MysterServerPoolImpl.class.getName());

    // MysterIPPool stores all its ips
    private static final String PREF_NODE_NAME = "Tracker.MysterIPPool";
    
    private final Map<MysterIdentity, WeakReference<MysterServerImplementation>> cache;

    // if we failed to get stats for this it ends up here so we don't keep retrying
    private final DeadIPCache deadCache = new DeadIPCache();

    private final MysterProtocol protocol;
    private final Preferences preferences;
    private final IdentityTracker identityTracker;

    private final List<Consumer<MysterServer>> listeners = new ArrayList<>();

    private Map<MysterAddress, PromiseFuture<MysterServer>> outstandingServerFutures = new HashMap<>();
    
    private List<MysterServerImplementation> hardLinks = new ArrayList<>();

    public MysterServerPoolImpl(Preferences prefs, MysterProtocol mysterProtocol) {
        this.preferences = prefs.node(PREF_NODE_NAME);
        this.protocol = mysterProtocol;
        LOGGER.info("Loading IPPool.....");
        cache = new HashMap<>();
        identityTracker = new IdentityTracker(mysterProtocol.getDatagram()::ping);
        
        String[] dirList;
        try {
            dirList = preferences.childrenNames();
            for (String nodeName : dirList) {
                var serverNode = preferences.node(nodeName);
                
                Optional<MysterIdentity> identity = MysterServerImplementation.extractIdentity(serverNode, nodeName);
                if (identity.isEmpty() ) { 
                    serverNode.removeNode();
                    continue;
                }
                
//                MysterServerImplementation mysterip = create(serverNode, identityTracker, identity.get());
//                hardLinks.add(mysterip);
//                
//                addAddressesToIdentityTracker(serverNode, mysterip.getIdentity());
            }
        } catch (BackingStoreException exception) {
            // ignore
        }

        LOGGER.info("Loaded IPPool");
    }
    
    public void clearHardLinks() {
        hardLinks.clear();
    }

    @Override
    public MysterIdentity lookupIdentityFromName(ExternalName externalName) {
        return identityTracker.getIdentityFromExternalName(externalName);
    }

    private MysterServerImplementation create(Preferences node,
                                              IdentityProvider identityProvider,
                                              MysterIdentity identity) {
        var server = new MysterServerImplementation(node, identityTracker, identity);
        addToDataStructures(server);

        addAddressesToIdentityTracker(node, identity);

        return server;
    }

    private void addAddressesToIdentityTracker(Preferences node, MysterIdentity identity) {
        extractAddresses(node).forEach(a -> identityTracker.addIdentity(identity, a));
    }

    private synchronized MysterServerImplementation create(Preferences prefs,
                                                           IdentityProvider addressProvider,
                                                           RobustMML serverStats,
                                                           MysterIdentity identity,
                                                           MysterAddress address) {
        identityTracker.addIdentity(identity, address);
        var server = new MysterServerImplementation(prefs, identityTracker, serverStats, identity);
        addToDataStructures(server);

        return server;
    }

    private void addToDataStructures(MysterServerImplementation server) {
        cache.put(server.getIdentity(), new WeakReference<MysterServerImplementation>(server));

        var identity = server.getIdentity();
        TrackerUtils.CLEANER.register(server, () -> {
            removeServer(identity);
        });
    }

    private synchronized void removeServer(MysterIdentity identity) {
        if (!cache.containsKey(identity)) {
            return;
        }
        
        if (cache.get(identity).get()!= null) {
            return;
        }
        
        cache.remove(identity);
        
        for (MysterAddress address : identityTracker.getAddresses(identity)) {
            identityTracker.removeIdentity(identity, address);
        }

        try {
            preferences.node(computeNodeNameFromIdentity(identity).toString()).removeNode();
        } catch (BackingStoreException exception) {
            LOGGER.info("Could not delete MysterIP pref node for " + identity.toString());
        }
    }

    private List<MysterAddress> extractAddresses(Preferences serverNode) {
        String[] addresses = serverNode.get(MysterServerImplementation.ADDRESSES, "").split(" ");
        List<Optional<MysterAddress>> unfiltered =
                Util.map(Arrays.asList(addresses), addressString -> {
                    if (addressString.isBlank()) {
                       return  Optional.empty();
                    }
                    
                    try {
                        return Optional.of(new MysterAddress(addressString));
                    } catch (UnknownHostException ex) {
                        return Optional.empty();
                    }
                });

        return Util.map(Util.filter(unfiltered, a -> a.isPresent()), a -> a.get());
    }

    @Override
    public synchronized void addNewServerListener(Consumer<MysterServer> serverListener) {
        listeners.add(serverListener);
    }
    
    @Override
    public void removeNewServerListener(Consumer<MysterServer> serverListener) {
//        listeners.remove(serverListener);
        throw new IllegalStateException();
    }

    @Override
    public synchronized void suggestAddress(MysterAddress address) {
        getMysterServer(address)
                .addExceptionListener((e) -> LOGGER.info("Address not a server: " + address))
                .setInvoker(TrackerUtils.INVOKER);
    }

    private void fireNewServerEvent(MysterServer mysterServer) {
        List<Consumer<MysterServer>> l;
        
        synchronized (this) {
            l = new ArrayList<>(listeners);
        }
        
        for (Consumer<MysterServer> consumer : l) {
            consumer.accept(mysterServer);
        }
    }

    private static MysterIdentity extractIdentity(MysterAddress address, RobustMML serverStats) {
        if (!serverStats.pathExists(ServerStats.IDENTITY)) {
            return new MysterAddressIdentity(address);
        }
        
        String publicKeyAsString = serverStats.get(ServerStats.IDENTITY);
        if (publicKeyAsString == null) {
            return new MysterAddressIdentity(address);
        }

        return Util.publicKeyFromString(publicKeyAsString)
                .<MysterIdentity> map(PublicKeyIdentity::new)
                .orElse(new MysterAddressIdentity(address));
    }
    
    @Override
    public synchronized void suggestAddress(String address) {
        PromiseFutures.execute(() -> new MysterAddress(address))
                .setInvoker(TrackerUtils.INVOKER)
                .addResultListener(this::suggestAddress)
                .addExceptionListener((e) -> {
                    if (e instanceof UnknownHostException) {
                        LOGGER.info("Could not add this address to the pool, unknown host: " + address);
                        return;
                    }
                    
                    e.printStackTrace();
                });
    }

    @Override
    public synchronized boolean existsInPool(MysterAddress address) {
        return identityTracker.exists(address);
    }
    
    public synchronized PromiseFuture<MysterServer> getMysterServer(MysterAddress address)  {
        MysterIdentity identity = identityTracker.getIdentity(address);

        if (identity != null) {
            MysterServer temp = getCachedMysterServer(identity);

            if (temp != null) {
                return PromiseFuture.newPromiseFuture(temp);
            }
        }

        if (deadCache.isDeadAddress(address) || address.getIP().equals("0.0.0.0") || address.getIP().equals("")) {
            return PromiseFuture.newPromiseFuture(c -> c.setException(new IOException("IP is dead")));
        }

        return refreshMysterServer(address);
    }

    private PromiseFuture<MysterServer> refreshMysterServer(MysterAddress address) {
        if (outstandingServerFutures.containsKey(address)) {
            return outstandingServerFutures.get(address);
        }
        
        PromiseFuture<MysterServer> getServerFuture = PromiseFuture.newPromiseFuture(context -> {
            // cancel intentionally not hooked up

            protocol.getDatagram().getServerStats(address).clearInvoker()
                    .setInvoker(TrackerUtils.INVOKER).addResultListener(mml -> {
                        serverStatsCallback(address, context, mml);
                    }).addExceptionListener(context::setException)
                    .addExceptionListener(ex -> deadCache.addDeadAddress(address))
                    .addFinallyListener(() -> outstandingServerFutures.remove(address));
        });

        outstandingServerFutures.put(address, getServerFuture);

        return getServerFuture;
    }

    private synchronized void serverStatsCallback(MysterAddress address,
                                                  AsyncContext<MysterServer> context,
                                                  RobustMML mml) {
        try {
            int port = Integer.parseInt(mml.get(ServerStats.PORT));
            if (address.getPort() != port) {
                suggestAddress(new MysterAddress(address.getInetAddress(), port));
                LOGGER.info("Server at address "
                        + address + " should be on port "
                        + port + " will retry on that port");
                
                context.setResult(null);
                return;
            }

        } catch (Exception ex) {
            // doesn't matter
        }
        
        
        var i = extractIdentity(address, mml);

        if (identityTracker.existsMysterIdentity(i)) {
            identityTracker.addIdentity(i, address);
            
            WeakReference<MysterServerImplementation> weakReference = cache.get(i);
            var s = weakReference != null ? weakReference.get() : null;
            if ( s != null ) {
                s.refreshStats(mml);
            }
            
            
            // this is to make unit tests work - otherwise it's all async and a pain to test
            TrackerUtils.INVOKER.invoke(() -> fireNewServerEvent(s.getInterface()));
            
            context.setResult(s.getInterface());
            
            return;
        }

        MysterServerImplementation server =
                create(preferences.node(computeNodeNameFromIdentity(i).toString()),
                       identityTracker,
                       mml,
                       i,
                       address);

        // this is crap
        context.setResult(newOrGet(address, server));

        MysterServer serverinterface = server.getInterface();
        
        TrackerUtils.INVOKER.invoke(() -> fireNewServerEvent(serverinterface));
    }

    /**
     * In order to avoid having thread problems the two functions below are used.
     * They are required because the checking the index and getting the object
     * at that index should be atomic, hence the synchronised! and the two
     * functions (for two levels of checking
     */
    public synchronized MysterServer getCachedMysterServer(MysterIdentity k) {
        MysterServerImplementation mysterip = getMysterIP(k);

        if (mysterip == null)
            return null;

        return mysterip.getInterface();
    }
    
    public synchronized MysterServer getCachedMysterIp(MysterAddress address) {
        var identity = identityTracker.getIdentity(address);
        if (identity == null) {
            return null;
        }
        
        MysterServerImplementation mysterip = getMysterIP(identity);

        if (mysterip == null)
            return null;

        return mysterip.getInterface();
    }

    public boolean existsInPool(MysterIdentity k) {
        return (getMysterIP(k) != null);
    }

    private synchronized MysterServer newOrGet(MysterAddress address, MysterServerImplementation m) {
        MysterIdentity key = m.getIdentity(); // possible future bugs here...
        if (existsInPool(key)) {
            return getCachedMysterServer(key);
        }

        return addANewMysterObjectToThePool(address, m);
    }

    /**
     * this function adds a new IP to the MysterIPPool data structure.. It's
     * synchronized so it's thread safe.
     * 
     * The function double checks to make sure that there really hasen't been
     * another myster IP cached during the time it took to check and returns the
     * appropriate object.
     * @param address 
     */
    private synchronized MysterServer addANewMysterObjectToThePool(MysterAddress address, MysterServerImplementation ip) {
        if (existsInPool(ip.getIdentity())) {
            ip = getMysterIP(ip.getIdentity());
        }

        ip.save();
        
        return ip.getInterface();
    }

    private MysterServerImplementation getMysterIP(MysterIdentity k) {
        var s = cache.get(k);

        if (s == null) {
            return null;
        }

        return s.get();
    }
    
    /**
     * Intended to be used by unit tests since we don't tend to close myster server pool impls
     */
    void close() {
        identityTracker.close();
    }
}
