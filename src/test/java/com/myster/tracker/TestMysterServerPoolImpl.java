package com.myster.tracker;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.general.util.Semaphore;
import com.myster.identity.Identity;
import com.myster.mml.MessagePack;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.client.PingResponse;
import com.myster.type.MysterType;

class TestMysterServerPoolImpl {
    private static final Logger LOGGER = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
    private Map<MysterAddress, MessagePack> lookup;
    
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
    void setUp() throws UnknownHostException {

        lookup = new HashMap<>();
        
        // Build MessagePack server stats similar to TestMysterServerImplementation
        var pubKey = identity.getMainIdentity().get().getPublic();
        byte[] keyBytes = pubKey.getEncoded();
        
        MessagePack baseStats = MessagePack.newEmpty();
        baseStats.putString(com.myster.net.stream.server.ServerStats.SERVER_NAME, "Mr. Magoo");
        baseStats.putString(com.myster.net.stream.server.ServerStats.MYSTER_VERSION, "10");
        baseStats.putByteArray(com.myster.net.stream.server.ServerStats.IDENTITY, keyBytes);
        baseStats.putLong(com.myster.net.stream.server.ServerStats.UPTIME, 1000L);
        baseStats.putInt(com.myster.net.stream.server.ServerStats.NUMBER_OF_FILES + type, 42);
        
        // Addresses without explicit port
        lookup.put(MysterAddress.createMysterAddress("127.0.0.1"), copyOf(baseStats));
        lookup.put(MysterAddress.createMysterAddress("192.168.1.2"), copyOf(baseStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66"), copyOf(baseStats));
        
        // Stats with explicit port 7000
        MessagePack portStats = copyOf(baseStats);
        portStats.putInt(com.myster.net.stream.server.ServerStats.PORT, 7000);
        
        lookup.put(MysterAddress.createMysterAddress("192.168.1.2:7000"), copyOf(portStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66:7000"), copyOf(portStats));
        lookup.put(MysterAddress.createMysterAddress("24.20.25.66:6000"), copyOf(portStats));
        
        pref = new MapPreferences();
        protocol = new MysterProtocol() {
            @Override
            public MysterStream getStream() {
                throw new IllegalStateException("Not implemented");
            }

            @Override
            public MysterDatagram getDatagram() {
                MysterDatagram myMock = Mockito.mock(MysterDatagram.class);

                // Fix the ping mock - extract address from ParamBuilder
                Mockito.when(myMock.ping(Mockito.any()))
                        .thenAnswer(new Answer<PromiseFuture<PingResponse>>() {
                            @Override
                            public PromiseFuture<PingResponse> answer(InvocationOnMock invocation)
                                    throws Throwable {
                                ParamBuilder params = invocation.getArgument(0);
                                MysterAddress address = params.getAddress().orElseThrow();
                                return PromiseFuture.newPromiseFuture(new PingResponse(address, 1));
                            }
                        });

                // Fix the getServerStats mock - extract address from ParamBuilder  
                Mockito.when(myMock.getServerStats(Mockito.any()))
                        .thenAnswer(new Answer<PromiseFuture<MessagePack>>() {
                            @Override
                            public PromiseFuture<MessagePack> answer(InvocationOnMock invocation)
                                    throws Throwable {
                                ParamBuilder params = invocation.getArgument(0);
                                MysterAddress address = params.getAddress().orElseThrow();
                                MessagePack stats = lookup.get(address);
                                
                                if (stats == null) {
                                    return PromiseFuture.newPromiseFutureException(new IOException("Fake timeout"));
                                }
                                
                                return PromiseFuture.newPromiseFuture(stats);
                            }
                        });

                return myMock;
            }
        };
        
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

                LOGGER.info("Myster server is not there.. good.");

                break;
            }

            LOGGER.info("Myster server still there.. trying GC: " + (i + 1));
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
        
        var localHost = pool.getCachedMysterIp(MysterAddress.createMysterAddress("127.0.0.1")).get();
        var lanHost = pool.getCachedMysterIp(MysterAddress.createMysterAddress("192.168.1.2")).get();
        
        Assertions.assertEquals(localHost.getIdentity(), lanHost.getIdentity());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAddressImplSwicharoo(boolean shouldChangePort) throws Exception {
        MysterAddress oneTwoSeven = MysterAddress.createMysterAddress("127.0.0.1");
        MessagePack mml = lookup.get(oneTwoSeven);
        
        MessagePack copyMml = copyOf(mml);
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
        MessagePack restored = copyOf(copyMml);
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
            Assertions.assertTrue(pool.getCachedMysterIp(oneTwoSeven).isEmpty());
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
        
        var localHost = pool.getCachedMysterIp(MysterAddress.createMysterAddress("192.168.1.2")).get();
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
        
        var localHost = pool.getCachedMysterIp(MysterAddress.createMysterAddress("127.0.0.1")).get();
        var lanHost = pool.getCachedMysterIp(MysterAddress.createMysterAddress("192.168.1.2")).get();
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

        Assertions.assertNotNull(pool.getCachedMysterIp(MysterAddress.createMysterAddress("24.20.25.66:7000"))
                .toString());
    }

    private static MysterPoolListener convert(Consumer<MysterServer> c) {
        return new MysterPoolListener() {
            @Override
            public void serverRefresh(MysterServer server) {
                c.accept(server);
            }

            @Override
            public void serverPing(PingResponse pingResponse) {
                LOGGER.info("Ping response from " + pingResponse.address() + " in " + pingResponse.pingTimeMs() +"ms");
            }

            @Override
            public void deadServer(MysterIdentity identity) {
                // nothing
            }
        };

    }
    
    // Helper to deep copy a MessagePack (fromBytes(toBytes()))
    private static MessagePack copyOf(MessagePack src) {
        try {
            return MessagePack.fromBytes(src.toBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}