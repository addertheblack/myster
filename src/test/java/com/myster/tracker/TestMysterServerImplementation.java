package com.myster.tracker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.myster.identity.Identity;
import com.myster.mml.MMLException;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.client.PingResponse;
import com.myster.net.stream.server.ServerStats;
import com.myster.type.MysterType;

class TestMysterServerImplementation {
//   private static final Logger log = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    static Path tempDir; 

    private static String keystoreFilename = "testIdentity.keystore";
    private static File keystorePath;
    private static Identity identity;
    
    @BeforeAll
    static void setupAll() {
        keystorePath = tempDir.toFile();
        identity = new Identity(keystoreFilename, keystorePath);
    }

    @Test
    void testBasicsAlsoPing() throws UnknownHostException, MMLException, InterruptedException {
        Semaphore sem = new Semaphore(0);
        
        MapPreferences p = new MapPreferences();
        IdentityTracker it = new IdentityTracker(a -> PromiseFuture.newPromiseFuture(new PingResponse(a, 1)), (_)->{ sem.release(); }, (_)->{});
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1");
        
        
        PublicKey publicKey = identity.getMainIdentity().get().getPublic();
        MysterType type = new MysterType(publicKey);
        byte[] key = publicKey.getEncoded(); // this is the Full key now
        MysterIdentity id = new PublicKeyIdentity(publicKey);
        MessagePak stats = MessagePak.newEmpty();
        stats.putString(ServerStats.SERVER_NAME, "Mr. Magoo");
        stats.putString(ServerStats.MYSTER_VERSION, "10");
        stats.putByteArray(ServerStats.IDENTITY, key);
        stats.putLong(ServerStats.UPTIME, 1000l);
        stats.putInt(ServerStats.PORT, 1234);
        stats.putInt(ServerStats.NUMBER_OF_FILES + type, 0); // the toString of type is the right string value for here
        
        MysterServerImplementation impl = new MysterServerImplementation(p, it, stats, id, address);
        var addresses = impl.getAddresses();
        
        Assertions.assertArrayEquals(new MysterAddress[]{MysterAddress.createMysterAddress("127.0.0.1:1234")}, addresses);
        
        Assertions.assertEquals(MysterAddress.createMysterAddress("127.0.0.1:1234"), impl.getBestAddress().get());
        
        MysterServer server = impl.getInterface();
        Assertions.assertEquals("Mr. Magoo", server.getServerName()); 
        Assertions.assertArrayEquals(new MysterAddress[]{MysterAddress.createMysterAddress("127.0.0.1:1234")}, server.getAddresses()); 
        Assertions.assertEquals(MysterAddress.createMysterAddress("127.0.0.1:1234"), server.getBestAddress().get());
        sem.acquire();
        
        Assertions.assertArrayEquals(new MysterAddress[]{MysterAddress.createMysterAddress("127.0.0.1:1234")},  server.getUpAddresses()); 
    }

    @Test
    void descriptionIsBoundedPersistedAndClearedForLegacyStats() throws Exception {
        MapPreferences preferences = new MapPreferences();
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1");
        MysterIdentity serverIdentity = new MysterAddressIdentity(address);
        IdentityProvider provider = mock(IdentityProvider.class);
        when(provider.getAddresses(serverIdentity)).thenReturn(new MysterAddress[] { address });
        MessagePak stats = MessagePak.newEmpty();
        String fileCountPath = ServerStats.NUMBER_OF_FILES + new MysterType(new byte[16]);
        stats.putInt(fileCountPath, 0);
        stats.putString(ServerStats.SERVER_DESCRIPTION, "🙂".repeat(256));

        MysterServerImplementation impl = new MysterServerImplementation(
                preferences, provider, stats, serverIdentity, address);
        Assertions.assertEquals("🙂".repeat(255), impl.getInterface().getServerDescription());
        MysterServerImplementation reloaded = new MysterServerImplementation(
                preferences, provider, serverIdentity);
        Assertions.assertEquals("🙂".repeat(255), reloaded.getInterface().getServerDescription());

        stats.putString(ServerStats.SERVER_DESCRIPTION, "");
        impl.refreshStats(stats, address);
        reloaded = new MysterServerImplementation(preferences, provider, serverIdentity);
        Assertions.assertEquals("", reloaded.getInterface().getServerDescription());

        stats.putString(ServerStats.SERVER_DESCRIPTION, "Description before legacy update");
        impl.refreshStats(stats, address);
        MessagePak legacyStats = MessagePak.newEmpty();
        legacyStats.putInt(fileCountPath, 0);
        impl.refreshStats(legacyStats, address);
        Assertions.assertEquals("", impl.getInterface().getServerDescription());
        reloaded = new MysterServerImplementation(preferences, provider, serverIdentity);
        Assertions.assertEquals("", reloaded.getInterface().getServerDescription());

        preferences.remove(MysterServerImplementation.SERVER_DESCRIPTION);
        reloaded = new MysterServerImplementation(preferences, provider, serverIdentity);
        Assertions.assertEquals("", reloaded.getInterface().getServerDescription());
    }
}
