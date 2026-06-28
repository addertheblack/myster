package com.myster.tracker;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.general.util.Semaphore;
import com.myster.identity.Cid128;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.client.PingResponse;
import com.myster.type.MysterType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class TestMysterServerPoolImpl {
    private static final Logger log = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
    private Map<MysterAddress, MessagePak> lookup;
    private Set<MysterAddress> downPingAddresses;
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    static Path tempDir; 

    private static String keystoreFilename = "testIdentity.keystore";
    private static File keystorePath;
    private static Identity identity;
    private static MysterType type;
    
    private Preferences pref;
    private MysterProtocol protocol;

    private MysterServerPoolImpl pool;

    @BeforeAll
    static void beforeAll() {
        keystorePath = tempDir.toFile(); // Convert the Path to File, as your Identity class uses File
        identity = new Identity(keystoreFilename, keystorePath);
        type = new MysterType(identity.getMainIdentity().get().getPublic());
    }
    
    @BeforeEach
    void setUp() throws Exception {

        lookup = new HashMap<>();
        downPingAddresses = new HashSet<>();
        
        // Build MessagePack server stats similar to TestMysterServerImplementation
        var pubKey = identity.getMainIdentity().get().getPublic();
        MessagePak baseStats = serverStatsFor(pubKey, "Mr. Magoo");
        
        // Addresses without explicit port
        lookup.put(MysterAddress.createMysterAddress("127.0.0.1"), copyOf(baseStats));
        lookup.put(MysterAddress.createMysterAddress("192.168.1.2"), copyOf(baseStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66"), copyOf(baseStats));
        
        // Stats with explicit port 7000
        MessagePak portStats = copyOf(baseStats);
        portStats.putInt(com.myster.net.stream.server.ServerStats.PORT, 7000);
        
        lookup.put(MysterAddress.createMysterAddress("192.168.1.2:7000"), copyOf(portStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66:7000"), copyOf(portStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66:6000"), copyOf(portStats));
        
        pref = new MapPreferences();
        protocol = createProtocol();
        
        // public static final String NUMBER_OF_FILES = "/NumberOfFiles";
//        
//        public static final String MYSTER_VERSION = "/MysterVersion";
//        public static final String SPEED = "/Speed";
//        public static final String ADDRESS = "/Address"; 
//        public static final String SERVER_NAME = "/ServerName";
//        public static final String IDENTITY = "/Identity";
//        public static final String UPTIME = "/Uptime";
    }
    
    @Test
    void test() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);

        PublicKeyIdentity identity2 =
                new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        ExternalName externalName =
                MysterServerImplementation.computeNodeNameFromIdentity(identity2);

        Assertions.assertFalse(pool.existsInPool(identity2));
        Assertions.assertTrue(pool.lookupIdentityFromName(externalName).isEmpty());
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertFalse(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));

        Object[] moo = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        pool.addPoolListener(convert(s -> {
            moo[0] = s;

            latch.countDown();
        }));

        pool.suggestAddress("127.0.0.1");

        latch.await(30, TimeUnit.SECONDS);

        Assertions.assertEquals(((MysterServer) moo[0]).getAddresses().length, 1);

        Assertions.assertTrue(pool.existsInPool(identity2));
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        Assertions.assertTrue(pool.existsInPool(pool.lookupIdentityFromName(externalName).get()));

        moo[0] = null;

        for (int i = 0; i < 200; i++) {
            System.gc();System.gc();System.gc();
            System.gc();System.gc();System.gc();
            System.gc();System.gc();System.gc();
            Thread.sleep(1); // give some time for the cleanup thread to run..
            if (!pool.existsInPool(identity2)) {
                log.info("Myster server is not there.. good.");
                break;
            }

            log.info("Myster server still there.. trying GC: " + (i + 1));
        }

        Assertions.assertFalse(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        Assertions.assertFalse(pool.existsInPool(identity2));
        Assertions.assertTrue(pool.lookupIdentityFromName(externalName).isEmpty());
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertFalse(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));

        Object[] moo2 = new Object[1];
        CountDownLatch latch2 = new CountDownLatch(1);
        pool.addPoolListener(convert(s -> {
            moo2[0] = s;

            latch2.countDown();
        }));

        pool.suggestAddress("127.0.0.1");

        latch2.await(30, TimeUnit.SECONDS);

        Assertions.assertEquals(((MysterServer) moo2[0]).getAddresses().length, 1);

        Assertions.assertTrue(pool.existsInPool(identity2));
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        Assertions.assertTrue(pool.existsInPool(pool.lookupIdentityFromName(externalName).get()));
    }
    

    @Test
    void test2() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        Object[] moo = new Object[1];
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            moo[0] = s;
            
            sem.signal();
        }));
        
        pool.suggestAddress("127.0.0.1");
        sem.getLock();
        
        pool.suggestAddress("192.168.1.2");
        sem.getLock();
        
        Assertions.assertEquals(((MysterServer) moo[0]).getAddresses().length, 2);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertFalse(pool.existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        
        var localHost = pool.getCachedMysterServer(MysterAddress.createMysterAddress("127.0.0.1")).get();
        var lanHost = pool.getCachedMysterServer(MysterAddress.createMysterAddress("192.168.1.2")).get();
        
        Assertions.assertEquals(localHost.getIdentity(), lanHost.getIdentity());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAddressImplSwicharoo(boolean shouldChangePort) throws Exception {
        MysterAddress oneTwoSeven = MysterAddress.createMysterAddress("127.0.0.1");
        MessagePak mml = lookup.get(oneTwoSeven);
        
        MessagePak copyMml = copyOf(mml);
        byte[] identOld = copyMml.getByteArray(com.myster.net.stream.server.ServerStats.IDENTITY).orElse(null);
        copyMml.remove(com.myster.net.stream.server.ServerStats.IDENTITY);

        if (shouldChangePort) {
            copyMml.putInt(com.myster.net.stream.server.ServerStats.PORT, 1234);
        }
        
        lookup.put(oneTwoSeven, copyMml);
        
        pool = new MysterServerPoolImpl(pref, protocol);
        
        var refreshedServers = new ArrayList<MysterServer>();
        var deadServers = new ArrayList<MysterIdentity>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(new MysterPoolListener() {
            @Override
            public void serverRefresh(MysterServer server) {
                refreshedServers.add(server);
                sem.signal();
            }
            
            @Override
            public void serverPing(PingResponse server) {
                // nothing
            }
            
            @Override
            public void deadServer(MysterIdentity identity) {
                deadServers.add(identity);
                sem.signal();
            }
        });
        
        pool.suggestAddress("127.0.0.1");
        sem.getLock();

        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        
        Assertions.assertFalse(pool.existsInPool(identityPublic));
        Assertions.assertEquals(1, refreshedServers.size());
        
        var addressIdentity =  new MysterAddressIdentity(shouldChangePort ? MysterAddress.createMysterAddress("127.0.0.1:1234") : MysterAddress.createMysterAddress("127.0.0.1"));
        Assertions.assertTrue(pool.existsInPool(addressIdentity));
            
        // Restore identity to stats and update lookup
        MessagePak restored = copyOf(copyMml);
        if (identOld != null) {
            restored.putByteArray(com.myster.net.stream.server.ServerStats.IDENTITY, identOld);
        }
        lookup.put(oneTwoSeven, restored);
        
        pool.refreshMysterServer(oneTwoSeven);
        
        sem.getLock();
        sem.getLock();
        
        Assertions.assertEquals(2, refreshedServers.size());
        Assertions.assertEquals(1, deadServers.size());
        Assertions.assertEquals(addressIdentity, deadServers.get(0));
        
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        
        Assertions.assertTrue(pool.existsInPool(addressIdentity));
        Assertions.assertEquals(0, refreshedServers.get(0).getAddresses().length);
        Assertions.assertEquals(1, refreshedServers.get(1).getAddresses().length);
        if (shouldChangePort) {
            Assertions.assertTrue(pool.getCachedMysterServer(oneTwoSeven).isEmpty());
        }
    }
    
    /**
     * This test exists because of a bug found while doing practical tests
     * 
     * Essentially we were not adding the IP to the identityTracker until AFTER
     * the MysterServer had been inited. This isn't allowed as it causes the IP
     * to not be saved.
     */
    @Test
    void testPrefsStorageWithOnlyOne() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        
        pool = null;
        
        pool = new MysterServerPoolImpl(pref, protocol);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("192.168.1.2")));
        
        var localHost = pool.getCachedMysterServer(MysterAddress.createMysterAddress("192.168.1.2")).get();
        var serverFromCache = pool.getCachedMysterServer(identityPublic).get();
        
        Assertions.assertEquals(localHost.getIdentity(), serverFromCache.getIdentity());
        Assertions.assertEquals(localHost.getAddresses().length, 1);
    }
    
    @Test
    void testPrefsStorage() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("127.0.0.1");
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 2);
        
        pool = null;
        
        pool = new MysterServerPoolImpl(pref, protocol);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertFalse(pool.existsInPool(new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        
        var localHost = pool.getCachedMysterServer(MysterAddress.createMysterAddress("127.0.0.1")).get();
        var lanHost = pool.getCachedMysterServer(MysterAddress.createMysterAddress("192.168.1.2")).get();
        Assertions.assertEquals(localHost.getIdentity(), lanHost.getIdentity());
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic).get();
        
        Assertions.assertEquals((int)serverFromCache.getSpeed(), 1);
        Assertions.assertEquals(serverFromCache.getServerName(), "Mr. Magoo");
        Assertions.assertEquals(serverFromCache.getUptime(), 1000);
        Assertions.assertEquals(serverFromCache.getNumberOfFiles(type), 42);
        

    }
    
    @Test
    void testRefresh() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("127.0.0.1");
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 2);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("192.168.1.2")));
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic);
        Assertions.assertEquals(serverFromCache.get().getAddresses().length, 2);
        
        pool.suggestAddress("24.20.25.66:7000");
        pool.suggestAddress("192.168.1.2:7000");
        
        sem.getLock();
        sem.getLock();
        
        var serverFromCache2 = pool.getCachedMysterServer(identityPublic).get();
        Assertions.assertEquals(serverFromCache2.getAddresses().length, 2);
        
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:7000")));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("192.168.1.2:7000")));
    }
    
    @Test
    void testAutoPortChangeRefresh() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("127.0.0.1");
        
       sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("127.0.0.1")));
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic).get();
        Assertions.assertEquals(serverFromCache.getAddresses().length, 1);
        
        pool.suggestAddress("24.20.25.66:6000");
        
        sem.getLock();
        
        var serverFromCache2 = pool.getCachedMysterServer(identityPublic).get();
        Assertions.assertEquals(serverFromCache2.getAddresses().length, 1);
        Assertions.assertEquals(serverFromCache2.getAddresses()[0], MysterAddress.createMysterAddress("24.20.25.66:7000"));
        
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:7000")));
    }
    
    @Test
    void testAutoPortChangeWithOnlyOne() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("24.20.25.66:6000");
        
       sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        Assertions.assertEquals(captured.get(0).getAddresses().length, 1);
        Assertions.assertEquals(captured.get(0).getAddresses()[0], MysterAddress.createMysterAddress("24.20.25.66:7000"));
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:7000")));
        Assertions.assertFalse(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:6000")));
    }
    
    @Test
    void testDoubleSuggestCall() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("24.20.25.66:7000");
        pool.suggestAddress("24.20.25.66:7000");

        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        Assertions.assertEquals(captured.get(0).getAddresses().length, 1);
        Assertions.assertEquals(captured.get(0).getAddresses()[0], MysterAddress.createMysterAddress("24.20.25.66:7000"));
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:7000")));
        Assertions.assertFalse(pool.existsInPool(MysterAddress.createMysterAddress("24.20.25.66:6000")));
    }
    
    @Test
    public void testToString() throws InterruptedException, UnknownHostException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addPoolListener(convert(s -> {
            captured.add(s);
            
            sem.signal();
        }));
        
        pool.suggestAddress("24.20.25.66:7000");

        sem.getLock();

        Assertions.assertNotNull(pool.getCachedMysterServer(MysterAddress.createMysterAddress("24.20.25.66:7000"))
                .toString());
    }

    @Test
    void testFindClosestByCidReturnsExactLeftAndRight() throws Exception {
        List<PublicKeyIdentity> identities = addThreeDnsPreferenceServers(6, 80);
        pool = new MysterServerPoolImpl(pref, protocol);
        TrackerUtils.INVOKER.waitForThread();

        List<PublicKeyIdentity> ordered = new ArrayList<>(identities);
        ordered.sort(Comparator.comparing(TestMysterServerPoolImpl::cid));

        PublicKeyIdentity exact = ordered.get(2);
        IdentityNeighborSet neighbors = pool.findClosestByCid(cid(exact), 2);

        Assertions.assertEquals(exact, neighbors.exact().get());
        Assertions.assertEquals(List.of(ordered.get(1), ordered.get(0)), neighbors.left());
        Assertions.assertEquals(List.of(ordered.get(3), ordered.get(4)), neighbors.right());
    }

    @Test
    void testFindClosestByCidFiltersDownServers() throws Exception {
        List<PublicKeyIdentity> identities = addThreeDnsPreferenceServers(5, 100);
        List<PublicKeyIdentity> ordered = new ArrayList<>(identities);
        ordered.sort(Comparator.comparing(TestMysterServerPoolImpl::cid));
        PublicKeyIdentity downIdentity = ordered.get(2);

        MysterAddress downAddress = addressForIdentity(downIdentity);
        downPingAddresses.add(downAddress);

        pool = new MysterServerPoolImpl(pref, protocol);
        TrackerUtils.INVOKER.waitForThread();

        IdentityNeighborSet neighbors = pool.findClosestByCid(cid(downIdentity), 2);

        Assertions.assertTrue(neighbors.exact().isEmpty());
        Assertions.assertFalse(neighbors.left().contains(downIdentity));
        Assertions.assertFalse(neighbors.right().contains(downIdentity));
        Assertions.assertEquals(2, neighbors.left().size());
        Assertions.assertEquals(2, neighbors.right().size());
    }

    private static MysterPoolListener convert(Consumer<MysterServer> c) {
        return new MysterPoolListener() {
            @Override
            public void serverRefresh(MysterServer server) {
                c.accept(server);
            }

            @Override
            public void serverPing(PingResponse pingResponse) {
                log.info("Ping response from " + pingResponse.address() + " in " + pingResponse.pingTimeMs() +"ms");
            }

            @Override
            public void deadServer(MysterIdentity identity) {
                // nothing
            }
        };

    }
    
    // Helper to deep copy a MessagePack (fromBytes(toBytes()))
    private static MessagePak copyOf(MessagePak src) {
        try {
            return MessagePak.fromBytes(src.toBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<PublicKeyIdentity> addThreeDnsPreferenceServers(int count, int firstOctet)
            throws UnknownHostException {
        List<PublicKeyIdentity> identities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Identity identity = new Identity("threeDnsPool-" + firstOctet + "-" + i + ".keystore",
                                             tempDir.toFile());
            PublicKeyIdentity publicKeyIdentity =
                    new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
            identities.add(publicKeyIdentity);

            MysterAddress address = MysterAddress.createMysterAddress("24.20." + firstOctet + "." + (i + 1));
            String serverName = "Neighbor fixture " + i;
            lookup.put(address, serverStatsFor(publicKeyIdentity.getPublicKey(), serverName));
            saveServerPreference(publicKeyIdentity, address, serverName);
        }

        return identities;
    }

    private MysterAddress addressForIdentity(PublicKeyIdentity identity) {
        for (Map.Entry<MysterAddress, MessagePak> entry : lookup.entrySet()) {
            byte[] encoded = entry.getValue()
                    .getByteArray(com.myster.net.stream.server.ServerStats.IDENTITY)
                    .orElseThrow();
            if (java.util.Arrays.equals(encoded, identity.getPublicKey().getEncoded())) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("No address for identity");
    }

    private void saveServerPreference(PublicKeyIdentity identity,
                                      MysterAddress address,
                                      String serverName) {
        Preferences serverNode = pref.node("Tracker.MysterIPPool")
                .node(MysterServerImplementation.computeNodeNameFromIdentity(identity).toString());
        serverNode.put(MysterServerImplementation.SPEED, "1");
        serverNode.put(MysterServerImplementation.TIMEUP, "0");
        serverNode.put(MysterServerImplementation.TIMEDOWN, "0");
        serverNode.put(MysterServerImplementation.NUMBEROFHITS, "0");
        serverNode.put(MysterServerImplementation.TIMESINCEUPDATE, "0");
        serverNode.put(MysterServerImplementation.UPTIME, "1000");
        serverNode.put(MysterServerImplementation.SERVER_NAME, serverName);
        serverNode.put(MysterServerImplementation.IDENTITY_PUBLIC_KEY, identity.toString());
        serverNode.put(MysterServerImplementation.ADDRESSES, address.toString());
    }

    private static MessagePak serverStatsFor(PublicKey publicKey, String serverName) {
        MessagePak stats = MessagePak.newEmpty();
        stats.putString(com.myster.net.stream.server.ServerStats.SERVER_NAME, serverName);
        stats.putString(com.myster.net.stream.server.ServerStats.MYSTER_VERSION, "10");
        stats.putByteArray(com.myster.net.stream.server.ServerStats.IDENTITY, publicKey.getEncoded());
        stats.putLong(com.myster.net.stream.server.ServerStats.UPTIME, 1000L);
        stats.putInt(com.myster.net.stream.server.ServerStats.NUMBER_OF_FILES + type, 42);
        return stats;
    }

    private static Cid128 cid(PublicKeyIdentity identity) {
        return com.myster.identity.Util.generateCid(identity.getPublicKey());
    }

    private MysterProtocol createProtocol() throws IOException {
        MysterDatagram datagram = Mockito.mock(MysterDatagram.class, this::unexpectedProtocolCall);
        Mockito.doAnswer(invocation -> serverStats(invocation.getArgument(0)))
                .when(datagram)
                .getServerStats(Mockito.any(ParamBuilder.class));
        Mockito.doAnswer(invocation -> serverStats(invocation.getArgument(0)))
                .when(datagram)
                .getBidirectionalServerStats(Mockito.any(ParamBuilder.class));
        Mockito.doAnswer(invocation -> ping(invocation.getArgument(0)))
                .when(datagram)
                .ping(Mockito.any(ParamBuilder.class));

        MysterStream stream = Mockito.mock(MysterStream.class, this::unexpectedProtocolCall);
        Mockito.doAnswer(invocation -> streamSocket())
                .when(stream)
                .makeStreamConnection(Mockito.any(MysterAddress.class));
        Mockito.doReturn(true).when(stream).ping(Mockito.any(MysterSocket.class));

        MysterProtocol protocol = Mockito.mock(MysterProtocol.class, this::unexpectedProtocolCall);
        Mockito.doReturn(datagram).when(protocol).getDatagram();
        Mockito.doReturn(stream).when(protocol).getStream();
        return protocol;
    }

    private Object unexpectedProtocolCall(InvocationOnMock invocation) {
        String name = invocation.getMethod().getName();
        if (name.equals("toString")) {
            return "Unexpected-call mock for " + invocation.getMock().getClass().getInterfaces()[0].getSimpleName();
        }
        if (name.equals("hashCode")) {
            return System.identityHashCode(invocation.getMock());
        }
        if (name.equals("equals")) {
            return invocation.getMock() == invocation.getArgument(0);
        }

        throw new AssertionError("Unexpected protocol call: " + invocation.getMethod());
    }

    private PromiseFuture<MessagePak> serverStats(ParamBuilder params) {
        MysterAddress address = params.getAddress().orElseThrow();
        MessagePak stats = lookup.get(address);
        if (stats == null) {
            return PromiseFuture.newPromiseFutureException(new IOException("Fake timeout"));
        }

        return PromiseFuture.newPromiseFuture(stats);
    }

    private PromiseFuture<PingResponse> ping(ParamBuilder params) {
        MysterAddress address = params.getAddress().orElseThrow();
        int ping = downPingAddresses.contains(address) ? -1 : 1;
        return PromiseFuture.newPromiseFuture(new PingResponse(address, ping));
    }

    private MysterSocket streamSocket() throws IOException {
        MysterSocket socket = Mockito.mock(MysterSocket.class, this::unexpectedProtocolCall);
        Mockito.doNothing().when(socket).close();
        return socket;
    }
}
