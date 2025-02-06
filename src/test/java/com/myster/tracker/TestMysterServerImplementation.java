package com.myster.tracker;

import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.myster.client.datagram.PingResponse;
import com.myster.identity.Identity;
import com.myster.identity.Util;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;

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
        MysterAddress address = new MysterAddress("127.0.0.1");
        
        String cleanPublicKeyString = MML.cleanString(Util.keyToString(identity.getMainIdentity().get().getPublic()));
        MysterIdentity id = new PublicKeyIdentity(identity.getMainIdentity().get().getPublic());
        String mml = """
                <Speed>1</>
                <ServerName>Mr. Magoo</>
                <MysterVersion>10</>
                <Identity>%s</>
                <Uptime>1000</>
                <Port>1234</>
                <NumberOfFiles><MPG3>42</></>
                """.formatted(cleanPublicKeyString);
        
        MysterServerImplementation impl = new MysterServerImplementation(p, it, new RobustMML(mml), id, address);
        var addresses = impl.getAddresses();
        
        Assertions.assertArrayEquals(new MysterAddress[]{new MysterAddress("127.0.0.1:1234")}, addresses);
        
        Assertions.assertEquals(new MysterAddress("127.0.0.1:1234"), impl.getBestAddress().get());
        
        MysterServer server = impl.getInterface();
        Assertions.assertEquals("Mr. Magoo", server.getServerName()); 
        Assertions.assertArrayEquals(new MysterAddress[]{new MysterAddress("127.0.0.1:1234")}, server.getAddresses()); 
        Assertions.assertEquals(new MysterAddress("127.0.0.1:1234"), server.getBestAddress().get());
        sem.acquire();
        
        Assertions.assertArrayEquals(new MysterAddress[]{new MysterAddress("127.0.0.1:1234")},  server.getUpAddresses()); 
    }
}
