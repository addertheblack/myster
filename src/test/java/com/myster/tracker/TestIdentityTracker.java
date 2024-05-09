package com.myster.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.general.thread.PromiseFuture;
import com.myster.client.datagram.PingResponse;
import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
import com.myster.tracker.IdentityTracker.Pinger;

public class TestIdentityTracker {
    @Nested
    class TestOnlinePinger {
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
            identityTracker = new IdentityTracker(mockPinger, (_) -> {});
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
                addresses[i] = new MysterAddress("169.254.196." + (i + 2));
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
            MysterIdentity mysterIdentity = new PublicKeyIdentity(publicKey);

            PublicKey publicKey2 = createTestPublicKey();
            MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

            MysterAddress[] addresses = new MysterAddress[10];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = new MysterAddress("169.254.196." + (i + 2));
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
            MysterIdentity mysterIdentity = new PublicKeyIdentity(publicKey);

            PublicKey publicKey2 = createTestPublicKey();
            MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

            MysterIdentity mysterIdentity3 =
                    new MysterAddressIdentity(new MysterAddress("169.254.196.1"));

            MysterAddress[] addresses = new MysterAddress[10];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = new MysterAddress("169.254.196." + (i + 2));
            }

            MysterAddress[] addresses2 = new MysterAddress[addresses.length];
            for (int i = 0; i < addresses2.length; i++) {
                addresses2[i] = new MysterAddress("169.254.200." + (i + 2));
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

            assertEquals(mysterIdentity3,
                         identityTracker.getIdentity(new MysterAddress("169.254.196.1")));
        }


        @Test
        public void testIsUpAfterPing() throws InterruptedException {
            identityTracker.addIdentity(testIdentity, testAddress);
            identityTracker.waitForPing(testAddress);
            assertTrue(identityTracker.isUp(testAddress));
        }

        @Test
        public void testGetBestAddress() throws IOException, InterruptedException {
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
        public void testDeadServerListener()
                throws IOException, InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                counter.incrementAndGet();
                latch.countDown();
            };
            
            identityTracker.setDeadServerListener(deadServerListener);
            
            identityTracker.addIdentity(testIdentity, new MysterAddress("11.10.10.1"));
            identityTracker.removeIdentity(testIdentity, new MysterAddress("11.10.10.1"));
            
            latch.await();
            
            Assertions.assertEquals(1, counter.get());
        }
        
        @Test
        public void testDeadServerListenerComplex()
                throws IOException, InterruptedException {
            AtomicReference<MysterIdentity> identRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                identRef.set(r);
                latch.countDown();
            };
            
            identityTracker.setDeadServerListener(deadServerListener);
            
            identityTracker.addIdentity(testIdentity, new MysterAddress("11.10.10.1"));
            identityTracker.addIdentity(testIdentity, new MysterAddress("11.10.10.2"));
            identityTracker.addIdentity(testIdentity, new MysterAddress("11.10.10.3"));
            identityTracker.addIdentity(testIdentity, new MysterAddress("11.10.10.4"));
            
            identityTracker.removeIdentity(testIdentity, new MysterAddress("11.10.10.1"));
            identityTracker.removeIdentity(testIdentity, new MysterAddress("11.10.10.2"));
            identityTracker.removeIdentity(testIdentity, new MysterAddress("11.10.10.4"));

            Assertions.assertFalse(latch.await(1, TimeUnit.SECONDS));
            Assertions.assertTrue(identityTracker.existsMysterIdentity(testIdentity));
            
            identityTracker.removeIdentity(testIdentity, new MysterAddress("11.10.10.3"));
            
            Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));

            Assertions.assertEquals(testIdentity, identRef.get());
            
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity));
        }
        
        @Test
        public void testDeadServerListenerComplex2()
                throws IOException, InterruptedException {
            AtomicReference<MysterIdentity> identRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                identRef.set(r);
                latch.countDown();
            };
            
            identityTracker.setDeadServerListener(deadServerListener);
            
            MysterAddressIdentity testIdentity2 = new MysterAddressIdentity(new MysterAddress("11.10.10.2")) ;
            MysterAddressIdentity testIdentity3 = new MysterAddressIdentity(new MysterAddress("11.10.10.3")) ;
            
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity2));
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity));
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity3));
            
            identityTracker.addIdentity(testIdentity, new MysterAddress("127.0.0.1"));
            identityTracker.addIdentity(testIdentity2, new MysterAddress("11.10.10.2"));
            identityTracker.addIdentity(testIdentity3, new MysterAddress("11.10.10.3"));
            
            identityTracker.removeIdentity(testIdentity2, new MysterAddress("11.10.10.2"));

            Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS));

            Assertions.assertEquals(testIdentity2, identRef.get());
            
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity2));
            Assertions.assertTrue(identityTracker.existsMysterIdentity(testIdentity));
            Assertions.assertTrue(identityTracker.existsMysterIdentity(testIdentity3));
        }
    }

    @Nested
    class TestOfflinePinger {
        private IdentityTracker identityTracker;
        private PublicKeyIdentity mysterIdentity;

        @BeforeEach
        private void setup() throws IOException {
            Pinger pinger = (a) -> {
                return PromiseFuture
                        .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, -1)))
                        .setInvoker(TrackerUtils.INVOKER);
            };
            identityTracker = new IdentityTracker(pinger, (_) -> {});
            PublicKey publicKey = createTestPublicKey();
            mysterIdentity = new PublicKeyIdentity(publicKey);
        }

        @Test
        public void testGetBestAddressOfflineWithLoopback() throws IOException, InterruptedException {
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
        public void testGetBestAddressOffline() throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, new MysterAddress("192.168.1.1"));
            identityTracker.addIdentity(mysterIdentity, new MysterAddress("11.10.10.1"));

            identityTracker.waitForPing(new MysterAddress("192.168.1.1"));
            identityTracker.waitForPing(new MysterAddress("11.10.10.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), new MysterAddress("11.10.10.1"));
        }
    }
    

    @Nested
    class TestPingEvents {
        private PublicKeyIdentity mysterIdentity;
        private Pinger pinger;

        @BeforeEach
        private void setup() throws IOException {
            pinger = (a) -> {
                return PromiseFuture
                        .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, -1)))
                        .setInvoker(TrackerUtils.INVOKER);
            };

            PublicKey publicKey = createTestPublicKey();
            mysterIdentity = new PublicKeyIdentity(publicKey);
        }

        @Test
        public void testBasicPingEvents()
                throws IOException, InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<PingResponse> pingListener = (r) -> {
                counter.incrementAndGet();
                latch.countDown();
            };
            
            IdentityTracker identityTracker = new IdentityTracker(pinger, pingListener);
            
            identityTracker.addIdentity(mysterIdentity, new MysterAddress("11.10.10.1"));
            
            latch.await();
            
            Assertions.assertEquals(1, counter.get());
        }
    }
    

    private static PublicKey createTestPublicKey() throws IOException {
        File f = File.createTempFile("TestIdentityTracker", null);
        f.deleteOnExit();
        f.delete(); // we want to create a new store..

        Identity identity = new Identity(f.getName(), f.getParentFile());

        PublicKey publicKey = identity.getMainIdentity().get().getPublic();
        return publicKey;
    }
}
