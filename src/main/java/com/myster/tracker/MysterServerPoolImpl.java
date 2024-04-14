
package com.myster.tracker;

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

import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.Util;
import com.myster.client.net.MysterProtocol;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.server.stream.ServerStats;

public class MysterServerPoolImpl implements MysterServerPool {
    private static final Logger LOGGER = Logger.getLogger(MysterServerPoolImpl.class.getName());

    // MysterIPPool stores all its ips
    private static final String PREF_NODE_NAME = "Tracker.MysterIPPool";
    
    private final Map<MysterIdentity, WeakReference<MysterServerImplementation>> cache;

    /**
     * Given a string address, it returns a com.myster object. com.myster
     * objects are like little statistics objects. You can get these objects and
     * use these objects from anywhere in the program thanks to the new garbage
     * collector based system.
     */
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
                
                Optional<MysterIdentity> identity = extractIdentity(serverNode, nodeName);
                if (identity.isEmpty() ) { 
                    serverNode.removeNode();
                    continue;
                }
                
                MysterServerImplementation mysterip = create(serverNode, identityTracker, identity.get());
                hardLinks.add(mysterip);
                
                var addresses = extractAddresses(serverNode);
                for (MysterAddress address : addresses) {
                    identityTracker.addIdentity(mysterip.getIdentity(), address);
                }
            }
        } catch (BackingStoreException exception) {
            // ignore
        }

        LOGGER.info("Loaded IPPool");
    }
    
    public void clearHardLinks() {
        hardLinks.clear();
    }

    private MysterServerImplementation create(Preferences node,
                                              IdentityProvider identityProvider,
                                              MysterIdentity identity) {
        var server = new MysterServerImplementation(node, identityTracker, identity);
        addToDataStructures(server);

        return server;
    }

    private synchronized MysterServerImplementation create(Preferences prefs,
                                                           IdentityProvider addressProvider,
                                                           RobustMML serverStats,
                                                           MysterIdentity identity,
                                                           MysterAddress address) {
        var server = new MysterServerImplementation(prefs, identityTracker, serverStats, identity);
        addToDataStructures(server);
        identityTracker.addIdentity(server.getIdentity(), address);

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
            preferences.node(computeNodeNameFromIdentity(identity)).removeNode();
        } catch (BackingStoreException exception) {
            LOGGER.info("Could not delete MysterIP pref node for " + identity.toString());
        }
    }

    private List<MysterAddress> extractAddresses(Preferences serverNode) {
        List<String> stringAddresses = Arrays.asList(serverNode.get(MysterServerImplementation.ADDRESSES, "")
                .split(" "));
        
        // Java streams API is allota words for nuttin
        return Util.filter(Util.map(stringAddresses, sa -> {
            try {
                return new MysterAddress(sa);
            } catch (UnknownHostException ex) {
                return null;
            }
        }), a -> a != null);
    }

    @Override
    public synchronized void addNewServerListener(Consumer<MysterServer> serverListener) {
        listeners.add(serverListener);
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
    
    private static Optional<MysterIdentity> extractIdentity(Preferences serverPrefs, String md5HashOfIdentity) {
        var publicKeyAsString =
                serverPrefs.get(MysterServerImplementation.IDENTITY_PUBLIC_KEY, null);
        if (publicKeyAsString != null) {
            var identityPublicKey = Util.publicKeyFromString(publicKeyAsString);
            if (identityPublicKey.isEmpty()) {
                LOGGER.warning("identityPublicKey in the prefs seem to be corrupt: " + publicKeyAsString);
                
                return Optional.empty(); // sigh corruption
            }

            if (!Util.getMD5Hash(publicKeyAsString).equals(md5HashOfIdentity)) {
                LOGGER.warning("The md5 of the identity in the prefs and the identity in the server don't match. pref key:"
                        + md5HashOfIdentity + " vs in server structure: " + identityPublicKey);

                return Optional.empty();
            }
            
            return identityPublicKey.<MysterIdentity> map(PublicKeyIdentity::new);
        }
        
        String address = serverPrefs.get(MysterServerImplementation.IDENTITY_ADDRESS, null);
        if (address == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new MysterAddressIdentity(new MysterAddress(address)));
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
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
            MysterServer temp = getCachedMysterIp(identity);

            if (temp != null) {
                return PromiseFuture.newPromiseFuture(temp);
            }
        }

        if (deadCache.isDeadAddress(address) || address.getIP().equals("0.0.0.0") || address.getIP().equals("")) {
            return PromiseFuture.newPromiseFuture(c -> c.setException(new IOException("IP is dead")));
        }

        if (outstandingServerFutures.containsKey(address)) {
            return outstandingServerFutures.get(address);
        }
        
        PromiseFuture<MysterServer> getServerFuture = PromiseFuture.newPromiseFuture(context -> {
            // cancel intentionally not hooked up

            protocol.getDatagram().getServerStats(address).clearInvoker()
                    .setInvoker(TrackerUtils.INVOKER).addResultListener(mml -> {
                        var i = extractIdentity(address, mml);
                        MysterServerImplementation server = create(preferences
                                .node(computeNodeNameFromIdentity(i)), identityTracker, mml, i, address);

                        context.setResult(newOrGet(address, server));

                        MysterServer serverinterface = server.getInterface();
                        
                        TrackerUtils.INVOKER.invoke(() -> fireNewServerEvent(serverinterface));
                    })
                    .addExceptionListener(context::setException)
                    .addExceptionListener(ex -> deadCache.addDeadAddress(address))
                    .addFinallyListener(() -> outstandingServerFutures.remove(address));
        });
        
        outstandingServerFutures.put(address, getServerFuture);
        
        return getServerFuture;
    }

    /**
     * In order to avoid having thread problems the two functions below are used.
     * They are required because the checking the index and getting the object
     * at that index should be atomic, hence the synchronised! and the two
     * functions (for two levels of checking
     */
    public synchronized MysterServer getCachedMysterIp(MysterIdentity k) {
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
            return getCachedMysterIp(key);
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

//    /**
//     * This method can be invoked whenever the program feels it has too many
//     * MysterIP objects. This method will only delete objects not being used by
//     * the rest of the program.
//     */
//    private synchronized void deleteUseless() {
//        if (cache.size() <= GC_UPPER_LIMIT)
//            return;
//
//        Iterator<MysterIdentity> iterator = cache.keySet().iterator();
//        List<MysterIdentity> keysToDelete = new ArrayList<>();
//
//        // Collect worthless....
//        while (iterator.hasNext()) {
//            MysterIdentity workingKey = iterator.next();
//
//            MysterServerImplementation mysterip = cache.get(workingKey);
//
//            if ((mysterip.getMysterCount() <= 0) && (!mysterip.getStatus())) {
//                keysToDelete.add(workingKey);
//            }
//        }
//
//        // remove worthless...
//        for (MysterIdentity workingKey : keysToDelete) {
//            var mysterServer = cache.remove(workingKey); // weeee...
//            
//            if (mysterServer == null) {
//                continue;
//            }
//            
//            MysterAddress[] addresses = identityTracker.getAddresses(mysterServer.getIdentity());
//            for (MysterAddress address : addresses) {
//                identityTracker.removeIdentity(mysterServer.getIdentity(), address);
//            }
//        }
//        
//
//        // brag about it...
//        if (keysToDelete.size() >= 100) {
//            LOGGER.info(keysToDelete.size()
//                    + " useless MysterIP objects found. Cleaning up...");
//            System.gc();
//            LOGGER.info("Deleted " + keysToDelete.size()
//                    + " useless MysterIP objects from the Myster pool.");
//        }
//
//        LOGGER.info("IPPool : Removed " + keysToDelete.size()
//                + " object from the pool. There are now " + cache.size() + " objects in the pool");
//
//        // signal that the changes should be saved asap...
//        for (MysterIdentity key : keysToDelete) {
//            try {
//                preferences.node(computeNodeNameFromIdentity(key)).removeNode();
//            } catch (BackingStoreException exception) {
//                LOGGER.info("Could not delete MysterIP pref node for " + key.toString());
//            }
//        }
//    }

    private String computeNodeNameFromIdentity(MysterIdentity key) {
        return Util.getMD5Hash(key.toString());
    }

    private MysterServerImplementation getMysterIP(MysterIdentity k) {
        var s = cache.get(k);

        if (s == null) {
            return null;
        }

        return s.get();
    }
}
