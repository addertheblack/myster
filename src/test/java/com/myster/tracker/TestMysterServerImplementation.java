package com.myster.tracker;

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
import com.myster.client.datagram.PingResponse;
import com.myster.identity.Identity;
import com.myster.mml.MMLException;
import com.myster.mml.MessagePack;
import com.myster.net.MysterAddress;
import com.myster.server.stream.ServerStats;
import com.myster.type.MysterType;

class TestMysterServerImplementation {
//   private static final Logger LOGGER = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
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
        MessagePack stats = MessagePack.newEmpty();
        stats.put(ServerStats.SERVER_NAME, "Mr. Magoo");
        stats.put(ServerStats.MYSTER_VERSION, "10");
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
}
