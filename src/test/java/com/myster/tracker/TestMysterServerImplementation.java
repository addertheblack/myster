package com.myster.tracker;

import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.general.util.Util;
import com.myster.client.datagram.PingResponse;
import com.myster.identity.Identity;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;

class TestMysterServerImplementation {
//   private static final Logger LOGGER = Logger.getLogger(TestMysterServerPoolImpl.class.getName());
    
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    Path tempDir; 

    private String keystoreFilename = "testIdentity.keystore";
    private File keystorePath;
    private Identity identity;
    

    @Test
    void test() throws UnknownHostException, MMLException {
        keystorePath = tempDir.toFile(); // Convert the Path to File, as your Identity class uses File
        identity = new Identity(keystoreFilename, keystorePath);
        
        
        MapPreferences p = new MapPreferences();
        IdentityTracker it = new IdentityTracker(a -> PromiseFuture.newPromiseFuture(new PingResponse(a, 1)));
        MysterAddress address = new MysterAddress("127.0.0.1");
        
        String cleanPublicKeyString = MML.cleanString(Util.publicKeyToString(identity.getMainIdentity().get().getPublic()));
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
        
        Assertions.assertArrayEquals(addresses, new MysterAddress[]{new MysterAddress("127.0.0.1:1234")});
        
    }

}
