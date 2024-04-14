package com.myster.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.general.thread.PromiseFuture;
import com.myster.client.datagram.PingResponse;
import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
import com.myster.tracker.IdentityTracker.Pinger;

public class TestIdentityTracker {
    private IdentityTracker identityTracker;
    private Pinger mockPinger;
    private MysterAddress testAddress;
    private MysterIdentity testIdentity;

    @BeforeEach
    public void setUp() throws UnknownHostException {
        mockPinger = (a) -> {
            return PromiseFuture
                    .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, 1)))
                    .setInvoker(TrackerUtils.INVOKER);
        };
        identityTracker = new IdentityTracker(mockPinger);
        testAddress = new MysterAddress("127.0.0.1");
        testIdentity = new MysterAddressIdentity(new MysterAddress("127.0.0.1"));
    }

    @Test
    public void testAddAndRemoveIdentity() {
        assertFalse(identityTracker.exists(testAddress));

        identityTracker.addIdentity(testIdentity, testAddress);

        assertTrue(identityTracker.exists(testAddress));
        assertEquals(testIdentity, identityTracker.getIdentity(testAddress));

        identityTracker.removeIdentity(testIdentity, testAddress);

        assertFalse(identityTracker.exists(testAddress));
    }
    
    @Test
    public void testRemoveFromManyAddresses() throws IOException {
        PublicKey publicKey = createTestPublicKey();
        MysterIdentity mysterIdentity = new PublicKeyIdentity(publicKey);
        
        MysterAddress[] addresses = new MysterAddress[10];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] =  new MysterAddress("169.254.196." + (i + 2));
        }

        for (MysterAddress mysterAddress : addresses) {
            identityTracker.addIdentity(mysterIdentity, mysterAddress);
        }

        assertTrue(identityTracker.exists(addresses[0]));
        assertEquals(mysterIdentity, identityTracker.getIdentity(addresses[1]));
        
        assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity).length);

        identityTracker.removeIdentity(mysterIdentity, addresses[1]);
        
        assertEquals(addresses.length - 1, identityTracker.getAddresses(mysterIdentity).length);
        assertFalse(identityTracker.exists(addresses[1]));
        assertTrue(identityTracker.exists(addresses[2]));
        assertTrue(identityTracker.exists(addresses[0]));
    }
    
    @Test
    public void testAssociateAddressWithAnotherServer() throws IOException {
        PublicKey publicKey = createTestPublicKey();
        MysterIdentity mysterIdentity = new PublicKeyIdentity( publicKey);
        
        PublicKey publicKey2 = createTestPublicKey();
        MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

        MysterAddress[] addresses = new MysterAddress[10];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] =  new MysterAddress("169.254.196." + (i + 2));
        }

        for (MysterAddress mysterAddress : addresses) {
            identityTracker.addIdentity(mysterIdentity, mysterAddress);
        }

        assertTrue(identityTracker.exists(addresses[0]));
        assertEquals(mysterIdentity, identityTracker.getIdentity(addresses[1]));
        assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity).length);

        for (MysterAddress mysterAddress : addresses) {
            identityTracker.addIdentity(mysterIdentity2, mysterAddress);
        }
        
        assertEquals(0, identityTracker.getAddresses(mysterIdentity).length);
        assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity2).length);
        assertTrue(identityTracker.exists(addresses[1]));
        assertTrue(identityTracker.exists(addresses[2]));
        assertTrue(identityTracker.exists(addresses[0]));
        
        for (MysterAddress mysterAddress : addresses) {
            assertEquals(mysterIdentity2, identityTracker.getIdentity(mysterAddress));
        }
    }
    
    @Test
    public void testMixNMatch() throws IOException {
        PublicKey publicKey = createTestPublicKey();
        MysterIdentity mysterIdentity = new PublicKeyIdentity( publicKey);
        
        PublicKey publicKey2 = createTestPublicKey();
        MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

        MysterIdentity mysterIdentity3 =
                new MysterAddressIdentity(new MysterAddress("169.254.196.1"));

        MysterAddress[] addresses = new MysterAddress[10];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] =  new MysterAddress("169.254.196." + (i + 2));
        }
        
        MysterAddress[] addresses2 = new MysterAddress[addresses.length];
        for (int i = 0; i < addresses2.length; i++) {
            addresses2[i] =  new MysterAddress("169.254.200." + (i + 2));
        }

        for (MysterAddress mysterAddress : addresses) {
            identityTracker.addIdentity(mysterIdentity, mysterAddress);
        }
        
        for (MysterAddress mysterAddress : addresses2) {
            identityTracker.addIdentity(mysterIdentity2, mysterAddress);
        }
        
        identityTracker.addIdentity(mysterIdentity3, new MysterAddress("169.254.196.1"));

        
        assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity).length);
        assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity2).length);
        assertEquals(1, identityTracker.getAddresses(mysterIdentity3).length);
        
        for (MysterAddress mysterAddress : addresses) {
            assertEquals(mysterIdentity, identityTracker.getIdentity(mysterAddress));
        }
        
        for (MysterAddress mysterAddress : addresses2) {
            assertEquals(mysterIdentity2, identityTracker.getIdentity(mysterAddress));
        }
        
        assertEquals(mysterIdentity3, identityTracker.getIdentity(new MysterAddress("169.254.196.1")));
    }

    private PublicKey createTestPublicKey() throws IOException {
        File f = File.createTempFile("TestIdentityTracker", null);
        f.deleteOnExit();
        f.delete(); // we want to create a new store..
        
        Identity identity = new Identity(f.getName(), f.getParentFile());
        
        PublicKey publicKey = identity.getMainIdentity().get().getPublic();
        return publicKey;
    }

    @Test
    public void testIsUpAfterPing() {
        identityTracker.addIdentity(testIdentity, testAddress);
        identityTracker.waitForPing(testAddress);
        assertTrue(identityTracker.isUp(testAddress));
    }

    @Test
    public void testGetBestAddress() throws IOException {
        PublicKey publicKey = createTestPublicKey();
        MysterIdentity mysterIdentity = new PublicKeyIdentity(publicKey);
        identityTracker.addIdentity(mysterIdentity, new MysterAddress("127.0.0.1"));
        identityTracker.addIdentity(mysterIdentity, new MysterAddress("192.168.1.1"));
        identityTracker.addIdentity(mysterIdentity, new MysterAddress("11.10.10.1"));

        identityTracker.waitForPing(new MysterAddress("127.0.0.1"));
        identityTracker.waitForPing(new MysterAddress("192.168.1.1"));
        identityTracker.waitForPing(new MysterAddress("11.10.10.1"));

        Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

        assertTrue(bestAddress.isPresent());
        assertEquals(bestAddress.get(), new MysterAddress("127.0.0.1"));
    }
    
    @Test
    public void testGetBestAddressOffline() throws IOException {
        Pinger p = (a) -> {
            return PromiseFuture
                    .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, -1)))
                    .setInvoker(TrackerUtils.INVOKER);
        };
        
        IdentityTracker identityTracker2 = new IdentityTracker(p);
        
        PublicKey publicKey = createTestPublicKey();
        MysterIdentity mysterIdentity = new PublicKeyIdentity(publicKey);
        identityTracker2.addIdentity(mysterIdentity, new MysterAddress("127.0.0.1"));
        identityTracker2.addIdentity(mysterIdentity, new MysterAddress("192.168.1.1"));
        identityTracker2.addIdentity(mysterIdentity, new MysterAddress("11.10.10.1"));

        identityTracker2.waitForPing(new MysterAddress("127.0.0.1"));
        identityTracker2.waitForPing(new MysterAddress("192.168.1.1"));
        identityTracker2.waitForPing(new MysterAddress("11.10.10.1"));

        Optional<MysterAddress> bestAddress = identityTracker2.getBestAddress(mysterIdentity);

        assertTrue(bestAddress.isPresent());
        assertEquals(bestAddress.get(), new MysterAddress("11.10.10.1"));
    }
}
