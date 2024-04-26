
package com.myster.tracker;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.general.thread.PromiseFuture;
import com.general.util.Semaphore;
import com.general.util.Util;
import com.myster.client.datagram.PingResponse;
import com.myster.client.net.MysterDatagram;
import com.myster.client.net.MysterProtocol;
import com.myster.client.net.MysterStream;
import com.myster.identity.Identity;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

class TestMysterServerPoolImpl {
    private static final Logger LOGGER = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
    private Map<MysterAddress, RobustMML> lookup;
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    Path tempDir; 

    private String keystoreFilename = "testIdentity.keystore";
    private File keystorePath;
    private Identity identity;
    private Preferences pref;
    private MysterProtocol protocol;

    private MysterServerPoolImpl pool;

    @BeforeEach
    void setUp() throws UnknownHostException, MMLException {
        keystorePath = tempDir.toFile(); // Convert the Path to File, as your Identity class uses File
        identity = new Identity(keystoreFilename, keystorePath);
        lookup = new HashMap<>();
        
        String cleanPublicKeyString = MML.cleanString(Util.publicKeyToString(identity.getMainIdentity().get().getPublic()));
        String mml = """
                <Speed>1</>
                <ServerName>Mr. Magoo</>
                <MysterVersion>10</>
                <Identity>%s</>
                <Uptime>1000</>
                <NumberOfFiles><MPG3>42</></>
                """.formatted(cleanPublicKeyString);

        lookup.put(new MysterAddress("127.0.0.1"), new RobustMML(mml));
        lookup.put(new MysterAddress("192.168.1.2"), new RobustMML(mml));
        lookup.put(new MysterAddress("24.20.25.66"), new RobustMML(mml));
        
        String mml2 = """
                <Speed>1</>
                <ServerName>Mr. Magoo</>
                <MysterVersion>10</>
                <Identity>%s</>
                <Uptime>1000</>
                <Port>7000</>
                <NumberOfFiles><MPG3>42</></>
                """.formatted(cleanPublicKeyString);
        
        lookup.put(new MysterAddress("192.168.1.2:7000"), new RobustMML(mml2));
        lookup.put(new MysterAddress("24.20.25.66:7000"), new RobustMML(mml2));
        lookup.put(new MysterAddress("24.20.25.66:6000"), new RobustMML(mml2));
        
        pref = new MapPreferences();
        protocol = new MysterProtocol() {
            @Override
            public MysterStream getStream() {
                throw new IllegalStateException("Not implemented");
            }

            @Override
            public MysterDatagram getDatagram() {
                MysterDatagram myMock = Mockito.mock(MysterDatagram.class);

                Mockito.mock(MysterDatagram.class, invocation -> {
                    throw new UnsupportedOperationException("Not implemented");
                });

                Mockito.when(myMock.ping(Mockito.any()))
                        .thenAnswer(new Answer<PromiseFuture<PingResponse>>() {
                            @Override
                            public PromiseFuture<PingResponse> answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return PromiseFuture.newPromiseFuture(new PingResponse(invocation.getArgument(0), 1));
                            }
                        });

                Mockito.when(myMock.getServerStats(Mockito.any()))
                        .thenAnswer(new Answer<PromiseFuture<RobustMML>>() {
                            @Override
                            public PromiseFuture<RobustMML> answer(InvocationOnMock invocation)
                                    throws Throwable {
                                RobustMML robustMML = lookup.get(invocation.getArgument(0));
                                
                                if (robustMML==null) {
                                    return PromiseFuture.newPromiseFutureException(new IOException("Fake timeout"));
                                }
                                
                                return PromiseFuture.newPromiseFuture(robustMML);
                            }
                        });


                return myMock;
            }
        };
        
        
        
        
        
//        public static final String NUMBER_OF_FILES = "/NumberOfFiles";
//        
//        public static final String MYSTER_VERSION = "/MysterVersion";
//        public static final String SPEED = "/Speed";
//        public static final String ADDRESS = "/Address"; 
//        public static final String SERVER_NAME = "/ServerName";
//        public static final String IDENTITY = "/Identity";
//        public static final String UPTIME = "/Uptime";
    }
    
    @AfterEach
    void tearDown() {
        pool.close();
    }
    
    
    @Test
    void test() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);

        PublicKeyIdentity identity2 =
                new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        ExternalName externalName =
                MysterServerImplementation.computeNodeNameFromIdentity(identity2);

        Assertions.assertFalse(pool.existsInPool(identity2));
        Assertions.assertNull(pool.lookupIdentityFromName(externalName));
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(new MysterAddress("127.0.0.1"))));
        Assertions.assertFalse(pool.existsInPool(new MysterAddress("127.0.0.1")));

        Object[] moo = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        pool.addNewServerListener(s -> {
            moo[0] = s;

            latch.countDown();
        });

        pool.suggestAddress("127.0.0.1");

        latch.await(30, TimeUnit.SECONDS);

        Assertions.assertEquals(((MysterServer) moo[0]).getAddresses().length, 1);

        Assertions.assertTrue(pool.existsInPool(identity2));
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(new MysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("127.0.0.1")));
        Assertions.assertTrue(pool.existsInPool(pool.lookupIdentityFromName(externalName)));

        moo[0] = null;

        for (int i = 0; i < 20; i++) {
            System.gc();System.gc();System.gc();
            System.gc();System.gc();System.gc();
            System.gc();System.gc();System.gc();
            if (!pool.existsInPool(identity2)) {

                LOGGER.info("Myster server is not there.. good.");

                break;
            }

            LOGGER.info("Myster server still there.. trying GC: " + (i + 1));
        }

        Assertions.assertFalse(pool.existsInPool(new MysterAddress("127.0.0.1")));
        Assertions.assertFalse(pool.existsInPool(identity2));
        Assertions.assertFalse(pool
                .existsInPool(new MysterAddressIdentity(new MysterAddress("127.0.0.1"))));
        Assertions.assertNull(pool.lookupIdentityFromName(externalName));
    }
    

    @Test
    void test2() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        Object[] moo = new Object[1];
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            moo[0] = s;
            
            sem.signal();
        });
        
        pool.suggestAddress("127.0.0.1");
        sem.getLock();
        
        pool.suggestAddress("192.168.1.2");
        sem.getLock();
        
        Assertions.assertEquals(((MysterServer) moo[0]).getAddresses().length, 2);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertFalse(pool.existsInPool(new MysterAddressIdentity(new MysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("127.0.0.1")));
        
        var localHost = pool.getCachedMysterIp(new MysterAddress("127.0.0.1"));
        var lanHost = pool.getCachedMysterIp(new MysterAddress("192.168.1.2"));
        
        Assertions.assertEquals(localHost.getIdentity(), lanHost.getIdentity());
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
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        
        pool.close();
        pool = null;
        
        pool = new MysterServerPoolImpl(pref, protocol);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("192.168.1.2")));
        
        var localHost = pool.getCachedMysterIp(new MysterAddress("192.168.1.2"));
        var serverFromCache = pool.getCachedMysterServer(identityPublic);
        
        Assertions.assertEquals(localHost.getIdentity(), serverFromCache.getIdentity());
        Assertions.assertEquals(localHost.getAddresses().length, 1);
    }
    
    @Test
    void testPrefsStorage() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("127.0.0.1");
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 2);
        
        pool.close();
        pool = null;
        
        pool = new MysterServerPoolImpl(pref, protocol);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertFalse(pool.existsInPool(new MysterAddressIdentity(new MysterAddress("127.0.0.1"))));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("127.0.0.1")));
        
        var localHost = pool.getCachedMysterIp(new MysterAddress("127.0.0.1"));
        var lanHost = pool.getCachedMysterIp(new MysterAddress("192.168.1.2"));
        Assertions.assertEquals(localHost.getIdentity(), lanHost.getIdentity());
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic);
        
        Assertions.assertEquals((int)serverFromCache.getSpeed(), 1);
        Assertions.assertEquals(serverFromCache.getServerName(), "Mr. Magoo");
        Assertions.assertEquals(serverFromCache.getUptime(), 1000);
        Assertions.assertEquals(serverFromCache.getNumberOfFiles(new MysterType("MPG3")), 42);
        

    }
    
    @Test
    void testRefresh() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("127.0.0.1");
        pool.suggestAddress("192.168.1.2");
        
        sem.getLock();
        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 2);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("127.0.0.1")));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("192.168.1.2")));
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic);
        Assertions.assertEquals(serverFromCache.getAddresses().length, 2);
        
        pool.suggestAddress("24.20.25.66:7000");
        pool.suggestAddress("192.168.1.2:7000");
        
        sem.getLock();
        sem.getLock();
        
        var serverFromCache2 = pool.getCachedMysterServer(identityPublic);
        Assertions.assertEquals(serverFromCache2.getAddresses().length, 2);
        
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("24.20.25.66:7000")));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("192.168.1.2:7000")));
    }
    
    @Test
    void testAutoPortChangeRefresh() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("127.0.0.1");
        
       sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("127.0.0.1")));
        
        var serverFromCache = pool.getCachedMysterServer(identityPublic);
        Assertions.assertEquals(serverFromCache.getAddresses().length, 1);
        
        pool.suggestAddress("24.20.25.66:6000");
        
        sem.getLock();
        
        var serverFromCache2 = pool.getCachedMysterServer(identityPublic);
        Assertions.assertEquals(serverFromCache2.getAddresses().length, 1);
        Assertions.assertEquals(serverFromCache2.getAddresses()[0], new MysterAddress("24.20.25.66:7000"));
        
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("24.20.25.66:7000")));
    }
    
    @Test
    void testAutoPortChangeWithOnlyOne() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("24.20.25.66:6000");
        
       sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        Assertions.assertEquals(captured.get(0).getAddresses().length, 1);
        Assertions.assertEquals(captured.get(0).getAddresses()[0], new MysterAddress("24.20.25.66:7000"));
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("24.20.25.66:7000")));
        Assertions.assertFalse(pool.existsInPool(new MysterAddress("24.20.25.66:6000")));
    }
    
    @Test
    void testEgad() throws UnknownHostException, InterruptedException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("24.20.25.66:7000");
        pool.suggestAddress("24.20.25.66:7000");

        sem.getLock();
        
        Assertions.assertEquals(captured.size(), 1);
        Assertions.assertEquals(captured.get(0).getAddresses().length, 1);
        Assertions.assertEquals(captured.get(0).getAddresses()[0], new MysterAddress("24.20.25.66:7000"));
        
        PublicKeyIdentity identityPublic = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        Assertions.assertTrue(pool.existsInPool(identityPublic));
        Assertions.assertTrue(pool.existsInPool(new MysterAddress("24.20.25.66:7000")));
        Assertions.assertFalse(pool.existsInPool(new MysterAddress("24.20.25.66:6000")));
    }
    
    @Test
    public void testToString() throws InterruptedException, UnknownHostException {
        pool = new MysterServerPoolImpl(pref, protocol);
        
        List<MysterServer> captured = new ArrayList<>();
        Semaphore sem = new Semaphore(0);
        pool.addNewServerListener(s -> {
            captured.add(s);
            
            sem.signal();
        });
        
        pool.suggestAddress("24.20.25.66:7000");

        sem.getLock();
        
        Assertions.assertNotNull(pool.getCachedMysterIp(new MysterAddress("24.20.25.66:7000")).toString());
    }
}

/** This is an in-mem impl of a java pref node. At some point I should make this a util */
class MapPreferences extends Preferences {
    private final MapPreferences root;
    private final Optional<MapPreferences> parent;
    private final Map<String, MapPreferences> children = new HashMap<>();
    
    private final Map<String, Object> values = new HashMap<>();
    private String name;
    
    public MapPreferences(MapPreferences parent, MapPreferences root, String name) {
        this.root = root == null ? this : root;
        this.parent = Optional.ofNullable(parent);
        this.name = name == null ? "" : name;
    }

    public MapPreferences() {
        this(null, null, null);
    }
        
    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public String get(String key, String def) {
        return (String) getWhatever(key, def);
    }
    
    private Object getWhatever(String key, Object def) {
        Object whatever =  values.get(key);
        return whatever == null ? def : whatever;
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void clear() throws BackingStoreException {
        values.clear();
    }

    @Override
    public void putInt(String key, int value) {
        values.put(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        return (int) getWhatever(key, def);
    }

    @Override
    public void putLong(String key, long value) {
        values.put(key, value);
    }

    @Override
    public long getLong(String key, long def) {
        return (long) getWhatever(key, def);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return (boolean) getWhatever(key, def);
    }

    @Override
    public void putFloat(String key, float value) {
        values.put(key, value);        
    }

    @Override
    public float getFloat(String key, float def) {
        return (float) getWhatever(key, def);
    }

    @Override
    public void putDouble(String key, double value) {
        values.put(key, value);
    }

    @Override
    public double getDouble(String key, double def) {
        return (double) getWhatever(key, def);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        values.put(key, value);
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        return (byte[]) getWhatever(key, def);
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return new ArrayList<>(values.keySet()).toArray(new String[values.keySet().size()]);
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        return new ArrayList<>(children.keySet()).toArray(new String[children.keySet().size()]);
    }

    @Override
    public Preferences parent() {
        return parent.get();
    }

    @Override
    public Preferences node(String pathName) {
        if (pathName.startsWith("/")) {
            return root.node(pathName.substring(1));
        }
        
        int indexOfSlash = pathName.indexOf("/");
        String nameOfNode = null;
        if (indexOfSlash == -1) {
            nameOfNode = pathName;
        } else {
            nameOfNode = pathName.substring(0, indexOfSlash);
        }
        
        MapPreferences zoik = children.get(nameOfNode);
        if (zoik == null) {
            zoik = new MapPreferences(this, root, nameOfNode);
            children.put(nameOfNode, zoik);
        }
        
        return zoik;
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void removeNode() throws BackingStoreException {
        values.clear();
        children.clear();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String absolutePath() {
        return null;
    }

    @Override
    public boolean isUserNode() {
        return true;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void flush() throws BackingStoreException {
        
    }

    @Override
    public void sync() throws BackingStoreException {
        
    }

    @Override
    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
        
    }

    @Override
    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
        
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener ncl) {
        
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener ncl) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
        
    }

    @Override
    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
        
    }
}
