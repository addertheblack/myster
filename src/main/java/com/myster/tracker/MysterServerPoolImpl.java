
package com.myster.tracker;

import static com.myster.tracker.MysterServerImplementation.computeNodeNameFromIdentity;

import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.general.events.NewGenericDispatcher;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.server.ServerUtils;
import com.myster.server.stream.ServerStats;

/**
 * Given a string address, it returns a com.myster object. com.myster
 * objects are like little statistics objects. You can get these objects and
 * use these objects from anywhere in the program thanks to the new garbage
 * collector based system.
 */
public class MysterServerPoolImpl implements MysterServerPool {
    private static final Logger LOGGER = Logger.getLogger(MysterServerPoolImpl.class.getName());
    private static final java.util.Timer timer = new java.util.Timer();

    // MysterIPPool stores all its ips
    private static final String PREF_NODE_NAME = "Tracker.MysterIPPool";
    
    private final Map<MysterIdentity, WeakReference<MysterServerImplementation>> cache;

    // if we failed to get stats for this it ends up here so we don't keep retrying
    private final DeadIPCache deadCache = new DeadIPCache();

    private final MysterProtocol protocol;
    private final Preferences preferences;
    private final IdentityTracker identityTracker;

    private final NewGenericDispatcher<MysterPoolListener> dispatcher = new NewGenericDispatcher<>(MysterPoolListener.class, TrackerUtils.INVOKER);

    private final Map<MysterAddress, PromiseFuture<RobustMML>> outstandingServerFutures = new HashMap<>();
    
    // This is so we can stop the GC for garbage collecting our weakly references stuff until the IP lists have
    // had a change to get references to things
    private List<MysterServerImplementation> hardLinks = new ArrayList<>();
    private TimerTask task;

    public MysterServerPoolImpl(Preferences prefs, MysterProtocol mysterProtocol) {
        this.preferences = prefs.node(PREF_NODE_NAME);
        this.protocol = mysterProtocol;
        
        LOGGER.info("Loading IPPool.....");

        cache = new HashMap<>();

        identityTracker = new IdentityTracker(mysterProtocol.getDatagram()::ping,
                                              dispatcher.fire()::serverPing,
                                              dispatcher.fire()::deadServer);

        try {
            String[] dirList = preferences.childrenNames();
            for (String nodeName : dirList) {
                var serverNode = preferences.node(nodeName);
                
                Optional<MysterIdentity> identity = MysterServerImplementation.extractIdentity(serverNode, nodeName);
                if (identity.isEmpty() ) { 
                    serverNode.removeNode();
                    continue;
                }
                
                MysterServerImplementation mysterip = create(serverNode, identityTracker, identity.get());
                hardLinks.add(mysterip);
                
                addAddressesToIdentityTracker(serverNode, mysterip.getIdentity());
            }
        } catch (BackingStoreException exception) {
            // ignore
        }

        LOGGER.info("Loaded IPPool");
    }

    public void clearHardLinks() {
        hardLinks.clear();
    }
    
    public synchronized void startRefreshTimer() {
        if(task != null) {
            task.cancel();
        }
        
        task = new TimerTask() {
            @Override
            public void run() {
                refreshAllMysterServers();
            }
        };
        timer.schedule(task, 10000, 1000 * 60 * 10);
    }

    private void refreshAllMysterServers() {
        List<MysterAddress> copy = new ArrayList<>();
        synchronized (this) {
            copy = cache.values().stream()
                    .map(WeakReference::get)
                    .filter(s -> s != null)
                    .map(MysterServerImplementation::getBestAddress)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        }

        copy.forEach(this::refreshMysterServer);
    }

    @Override
    public MysterIdentity lookupIdentityFromName(ExternalName externalName) {
        return identityTracker.getIdentityFromExternalName(externalName);
    }

    @Override
    public synchronized void addPoolListener(MysterPoolListener serverListener) {
        dispatcher.addListener(serverListener);
    }
    
    @Override
    public void removePoolListener(MysterPoolListener serverListener) {
//        listeners.remove(serverListener);
        throw new IllegalStateException();
    }

    @Override
    public synchronized void suggestAddress(MysterAddress address) {
        MysterIdentity identity = identityTracker.getIdentity(address);

        if (identity != null) {
            MysterServer temp = getCachedMysterServer(identity);

            if (temp != null) {
                // If the identity and server are known but someone is
                // suggesting the IP then maybe we should double check to see if the IP
                // is up. If it's down it might be worth a re-pingSer
                identityTracker.suggestPing(address);
                
                return;
            }
        }

        if (deadCache.isDeadAddress(address) || address.getIP().equals("0.0.0.0") || address.getIP().equals("")) {
            return;
        }
        
        if (outstandingServerFutures.containsKey(address)) {
            return;
        }

        refreshMysterServer(address);
    }

    private static MysterIdentity extractIdentity(MysterAddress address, RobustMML serverStats) {
        if (!serverStats.pathExists(ServerStats.IDENTITY)) {
            return new MysterAddressIdentity(address);
        }
        
        String publicKeyAsString = serverStats.get(ServerStats.IDENTITY);
        if (publicKeyAsString == null) {
            return new MysterAddressIdentity(address);
        }

        return com.myster.identity.Util.publicKeyFromString(publicKeyAsString)
                .<MysterIdentity> map(PublicKeyIdentity::new)
                .orElse(new MysterAddressIdentity(address));
    }
    
    @Override
    public synchronized void suggestAddress(String address) {
        PromiseFutures.execute(() -> MysterAddress.createMysterAddress(address))
                .setInvoker(TrackerUtils.INVOKER) // invoker is the CALLBACK thread not the exec thread
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
    
    public synchronized Optional<MysterServer> getCachedMysterIp(MysterAddress address) {
        var identity = identityTracker.getIdentity(address);
        if (identity == null) {
            return Optional.empty();
        }
        
        MysterServerImplementation mysterip = getMysterIP(identity);

        if (mysterip == null)
            return Optional.empty();

        return Optional.of(mysterip.getInterface());
    }

    public boolean existsInPool(MysterIdentity k) {
        return (getMysterIP(k) != null);
    }

    /**
     * Package protected for unit tests
     */
    void refreshMysterServer(MysterAddress address) {
        PromiseFuture<RobustMML> getServerStatsFuture =
                protocol.getDatagram().getServerStats(address).clearInvoker()
                        .setInvoker(TrackerUtils.INVOKER).addResultListener(mml -> {
                            serverStatsCallback(address, mml);
                        })
                        .addExceptionListener(_ -> LOGGER.info("Address not a server: " + address))
                        .addExceptionListener(_ -> deadCache.addDeadAddress(address))
                        .addFinallyListener(() -> {
                            synchronized (MysterServerPoolImpl.this) {
                                outstandingServerFutures.remove(address);
                            }
                        }); // thread bug

        outstandingServerFutures.put(address, getServerStatsFuture);
    }

    private synchronized void serverStatsCallback(MysterAddress addressIn,
                                                  RobustMML mml) {
        MysterAddress address = MysterServerImplementation.extractCorrectedAddress(mml, addressIn);
        deleteAddressBasedIdentitiesOnWrongPort(addressIn, address);
        
        var i = extractIdentity(address, mml);

        WeakReference<MysterServerImplementation> weakReference = cache.get(i);
        var s = weakReference != null ? weakReference.get() : null;
        if (identityTracker.existsMysterIdentity(i) && s != null) {
            s.refreshStats(mml, address);

            // this is to make unit tests work - otherwise it's all async and a pain to test
            TrackerUtils.INVOKER.invoke(() -> dispatcher.fire().serverRefresh(s.getInterface()));
            
            return;
        }

        MysterServerImplementation server =
                create(preferences.node(computeNodeNameFromIdentity(i).toString()),
                       identityTracker,
                       mml,
                       i,
                       address);

        MysterServer serverinterface = server.getInterface();
        
        dispatcher.fire().serverRefresh(serverinterface);
    }

    private void deleteAddressBasedIdentitiesOnWrongPort(MysterAddress addressIn,
                                                         MysterAddress address) {
        if (!addressIn.equals(address)) {
            MysterIdentity addressInIdentity = identityTracker.getIdentity(addressIn);

            if (addressInIdentity != null) {
                identityTracker.removeIdentity(addressInIdentity, addressIn);
                
                WeakReference<MysterServerImplementation> other = cache.get(addressInIdentity);
                if (other!=null) {
                    MysterServerImplementation otherServer = other.get();
                    if (otherServer!= null) {
                        otherServer.save();
                    }
                }
            }
        }
    }
    
    /**
     * Intended to be used by unit tests since we don't tend to close myster
     * server pool impls
     * 
     * Note that unit tests don't tend to init the task thread, though.
     */
    synchronized void close() {
        identityTracker.close();

        if (task != null) {
            task.cancel();
        }
    }

    private MysterServerImplementation getMysterIP(MysterIdentity k) {
        var s = cache.get(k);

        if (s == null) {
            return null;
        }

        return s.get();
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
        var server = new MysterServerImplementation(prefs, identityTracker, serverStats, identity, address);
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
                        return Optional.of(MysterAddress.createMysterAddress(addressString));
                    } catch (UnknownHostException ex) {
                        return Optional.empty();
                    }
                });

        return Util.map(Util.filter(unfiltered, a -> a.isPresent()), a -> a.get());
    }

    @Override
    public void filter(Consumer<MysterServer> consumer) {
        synchronized (this) {
            for (WeakReference<MysterServerImplementation> weakRef : cache.values()) {
                MysterServerImplementation server = weakRef.get();
                if (server != null) {
                    consumer.accept(server.getInterface());
                }
            }
        }        
    }

    @Override
    public void receivedPing(MysterAddress ip) {
        // this doens't always work because the ping can come from a port that
        // isn't the same one
        // that the server is registered with. In fact this is the normal case
        // for servers on a different port.
        // This will cause the cache lookup
        // to fail. So we ignore this case for servers not on the LAN.
        // For LAN addresses we look for servers on alternate addresses and
        // check
        // which are down and then ping that
        // This could result in extra pings but whatever. It's on the LAN
        // anyway.
        // For servers on a LAN we use the default port to allow servers to be
        // discoverable.. So this code path is a nice to have.
        if (!ServerUtils.isLanAddress(ip.getInetAddress())) {
            return;
        }
        
        suggestAddress(ip);
    }
}
