package com.myster.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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
        
        private static MysterIdentity mysterIdentity;

        @BeforeAll
        private static void setUpAll() throws IOException {
            PublicKey publicKey = createTestPublicKey();
            mysterIdentity = new PublicKeyIdentity(publicKey);
        }
        
        @BeforeEach
        public void setUp() throws UnknownHostException {
            mockPinger = (a) -> {
                return PromiseFuture
                        .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, 1)))
                        .setInvoker(TrackerUtils.INVOKER);
            };
            identityTracker = new IdentityTracker(mockPinger, (_) -> {}, (_)->{});
            testAddress = MysterAddress.createMysterAddress("127.0.0.1");
            testIdentity = new MysterAddressIdentity(MysterAddress.createMysterAddress("127.0.0.1"));
        }

        @Test
        public void testAddAndRemoveIdentity() {
            assertFalse(identityTracker.exists(testAddress));

            identityTracker.addIdentity(testIdentity, testAddress);

            assertTrue(identityTracker.exists(testAddress));
            assertEquals(testIdentity, identityTracker.getIdentity(testAddress).get());

            identityTracker.removeIdentity(testIdentity, testAddress);

            assertFalse(identityTracker.exists(testAddress));
        }

        @Test
        public void testRemoveFromManyAddresses() throws IOException {
            MysterAddress[] addresses = new MysterAddress[10];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = MysterAddress.createMysterAddress("169.254.196." + (i + 2));
            }

            for (MysterAddress mysterAddress : addresses) {
                identityTracker.addIdentity(mysterIdentity, mysterAddress);
            }

            assertTrue(identityTracker.exists(addresses[0]));
            assertEquals(mysterIdentity, identityTracker.getIdentity(addresses[1]).get());

            assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity).length);

            identityTracker.removeIdentity(mysterIdentity, addresses[1]);

            assertEquals(addresses.length - 1, identityTracker.getAddresses(mysterIdentity).length);
            assertFalse(identityTracker.exists(addresses[1]));
            assertTrue(identityTracker.exists(addresses[2]));
            assertTrue(identityTracker.exists(addresses[0]));
        }

        @Test
        public void testAssociateAddressWithAnotherServer() throws IOException {
            PublicKey publicKey2 = createTestPublicKey();
            MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

            MysterAddress[] addresses = new MysterAddress[10];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = MysterAddress.createMysterAddress("169.254.196." + (i + 2));
            }

            for (MysterAddress mysterAddress : addresses) {
                identityTracker.addIdentity(mysterIdentity, mysterAddress);
            }

            assertTrue(identityTracker.exists(addresses[0]));
            assertEquals(mysterIdentity, identityTracker.getIdentity(addresses[1]).get());
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
                assertEquals(mysterIdentity2, identityTracker.getIdentity(mysterAddress).get());
            }
        }

        @Test
        public void testMixNMatch() throws IOException {
            PublicKey publicKey2 = createTestPublicKey();
            MysterIdentity mysterIdentity2 = new PublicKeyIdentity(publicKey2);

            MysterIdentity mysterIdentity3 =
                    new MysterAddressIdentity(MysterAddress.createMysterAddress("169.254.196.1"));

            MysterAddress[] addresses = new MysterAddress[10];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = MysterAddress.createMysterAddress("169.254.196." + (i + 2));
            }

            MysterAddress[] addresses2 = new MysterAddress[addresses.length];
            for (int i = 0; i < addresses2.length; i++) {
                addresses2[i] = MysterAddress.createMysterAddress("169.254.200." + (i + 2));
            }

            for (MysterAddress mysterAddress : addresses) {
                identityTracker.addIdentity(mysterIdentity, mysterAddress);
            }

            for (MysterAddress mysterAddress : addresses2) {
                identityTracker.addIdentity(mysterIdentity2, mysterAddress);
            }

            identityTracker.addIdentity(mysterIdentity3, MysterAddress.createMysterAddress("169.254.196.1"));


            assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity).length);
            assertEquals(addresses.length, identityTracker.getAddresses(mysterIdentity2).length);
            assertEquals(1, identityTracker.getAddresses(mysterIdentity3).length);

            for (MysterAddress mysterAddress : addresses) {
                assertEquals(mysterIdentity, identityTracker.getIdentity(mysterAddress).get());
            }

            for (MysterAddress mysterAddress : addresses2) {
                assertEquals(mysterIdentity2, identityTracker.getIdentity(mysterAddress).get());
            }

            assertEquals(mysterIdentity3,
                         identityTracker.getIdentity(MysterAddress.createMysterAddress("169.254.196.1")).get());
        }


        @Test
        public void testIsUpAfterPing() throws InterruptedException {
            identityTracker.addIdentity(testIdentity, testAddress);
            identityTracker.waitForPing(testAddress);
            assertTrue(identityTracker.isUp(testAddress));
        }

        @Test
        public void testGetBestAddress() throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("127.0.0.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("11.10.10.1"));

            identityTracker.waitForPing(MysterAddress.createMysterAddress("127.0.0.1"));
            identityTracker.waitForPing(MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.waitForPing(MysterAddress.createMysterAddress("11.10.10.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), MysterAddress.createMysterAddress("127.0.0.1"));
        }

        @Test
        public void testDeadServerListener() throws IOException, InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                counter.incrementAndGet();
                latch.countDown();
            };
            
            IdentityTracker identityTracker = new IdentityTracker(mockPinger, (_) -> {}, deadServerListener);

            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.1"));
            identityTracker.removeIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.1"));

            latch.await();

            Assertions.assertEquals(1, counter.get());
        }

        @Test
        public void testDeadServerListenerComplex() throws IOException, InterruptedException {
            AtomicReference<MysterIdentity> identRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                identRef.set(r);
                latch.countDown();
            };

            IdentityTracker identityTracker = new IdentityTracker(mockPinger, (_) -> {}, deadServerListener);

            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.1"));
            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.2"));
            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.3"));
            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.4"));

            identityTracker.removeIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.1"));
            identityTracker.removeIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.2"));
            identityTracker.removeIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.4"));

            Assertions.assertFalse(latch.await(1, TimeUnit.SECONDS));
            Assertions.assertTrue(identityTracker.existsMysterIdentity(testIdentity));

            identityTracker.removeIdentity(testIdentity, MysterAddress.createMysterAddress("11.10.10.3"));

            Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));

            Assertions.assertEquals(testIdentity, identRef.get());

            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity));
        }

        @Test
        public void testDeadServerListenerComplex2() throws IOException, InterruptedException {
            AtomicReference<MysterIdentity> identRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<MysterIdentity> deadServerListener = (r) -> {
                identRef.set(r);
                latch.countDown();
            };

            IdentityTracker identityTracker = new IdentityTracker(mockPinger, (_) -> {}, deadServerListener);

            MysterAddressIdentity testIdentity2 =
                    new MysterAddressIdentity(MysterAddress.createMysterAddress("11.10.10.2"));
            MysterAddressIdentity testIdentity3 =
                    new MysterAddressIdentity(MysterAddress.createMysterAddress("11.10.10.3"));

            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity2));
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity));
            Assertions.assertFalse(identityTracker.existsMysterIdentity(testIdentity3));

            identityTracker.addIdentity(testIdentity, MysterAddress.createMysterAddress("127.0.0.1"));
            identityTracker.addIdentity(testIdentity2, MysterAddress.createMysterAddress("11.10.10.2"));
            identityTracker.addIdentity(testIdentity3, MysterAddress.createMysterAddress("11.10.10.3"));

            identityTracker.removeIdentity(testIdentity2, MysterAddress.createMysterAddress("11.10.10.2"));

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
        private static PublicKeyIdentity mysterIdentity;

        @BeforeAll
        private static void setUpAll() throws IOException {
            PublicKey publicKey = createTestPublicKey();
            mysterIdentity = new PublicKeyIdentity(publicKey);
        }

        @BeforeEach
        private void setup() {
            Pinger pinger = (a) -> {
                return PromiseFuture
                        .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, -1)))
                        .setInvoker(TrackerUtils.INVOKER);
            };
            identityTracker = new IdentityTracker(pinger, (_) -> {}, (_)->{});
        }

        @Test
        public void testGetBestAddressOfflineWithLoopback()
                throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("127.0.0.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("11.10.10.1"));

            identityTracker.waitForPing(MysterAddress.createMysterAddress("127.0.0.1"));
            identityTracker.waitForPing(MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.waitForPing(MysterAddress.createMysterAddress("11.10.10.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), MysterAddress.createMysterAddress("127.0.0.1"));
        }

        @Test
        public void testGetBestAddressOffline() throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("11.10.10.1"));

            identityTracker.waitForPing(MysterAddress.createMysterAddress("192.168.1.1"));
            identityTracker.waitForPing(MysterAddress.createMysterAddress("11.10.10.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), MysterAddress.createMysterAddress("11.10.10.1"));
        }

        @Test
        public void testGetBestAddressOfflineLanAddressesOnly()
                throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("192.168.1.1"));

            identityTracker.waitForPing(MysterAddress.createMysterAddress("192.168.1.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), MysterAddress.createMysterAddress("192.168.1.1"));
        }

        @Test
        public void testGetBestAddressOfflineLanLoopbackOnly()
                throws IOException, InterruptedException {
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("127.0.0.1"));

            identityTracker.waitForPing(MysterAddress.createMysterAddress("127.0.0.1"));

            Optional<MysterAddress> bestAddress = identityTracker.getBestAddress(mysterIdentity);

            assertTrue(bestAddress.isPresent());
            assertEquals(bestAddress.get(), MysterAddress.createMysterAddress("127.0.0.1"));
        }
    }

    @Nested
    class TestCleanupOld {
        private PublicKeyIdentity mysterIdentity;
        private Pinger pinger;        
        
        @BeforeEach
        private void setup() throws IOException {
            pinger = (a) -> {
                int responseTime = -1;
                try {
                    if (a.equals(MysterAddress.createMysterAddress("10.10.10.1"))|| a.equals(MysterAddress.createMysterAddress("222.1.2.3"))) {
                        responseTime = 1;
                    }
                } catch (UnknownHostException e) {
                    throw new UncheckedIOException(e);
                }
                
                final int stupidWorkAround = responseTime;
                
                return PromiseFuture
                        .<PingResponse> newPromiseFuture(c -> c.setResult(new PingResponse(a, stupidWorkAround)))
                        .setInvoker(TrackerUtils.INVOKER);
            };

            PublicKey publicKey = createTestPublicKey();
            mysterIdentity = new PublicKeyIdentity(publicKey);
        }

        
        @Test
        public void testBasicFunctionality() throws IOException, InterruptedException {
            Consumer<PingResponse> pingListener = (_) -> {
            };

            IdentityTracker identityTracker = new IdentityTracker(pinger, pingListener, (_)->{});     
            
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("10.10.10.1"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("10.10.10.2"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("10.10.10.3"));
            
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("12.1.2.3"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("222.1.2.3"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("24.10.10.3"));
            
            TrackerUtils.INVOKER.waitForThread();
            
            MysterAddress[] addresses = identityTracker.getAddresses(mysterIdentity);
            Assertions.assertEquals(6, addresses.length);
            
            Optional<MysterAddress> best = identityTracker.getBestAddress(mysterIdentity);
            Assertions.assertTrue(best.isPresent());
            Assertions.assertEquals(MysterAddress.createMysterAddress("10.10.10.1"), best.get());
            
            Assertions.assertTrue(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.1")));
            Assertions.assertTrue(identityTracker.isUp(MysterAddress.createMysterAddress("222.1.2.3")));
            
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.2")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("24.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("12.1.2.3")));
            
            identityTracker.cleanUpOldAddresses(mysterIdentity);
            Assertions.assertEquals(2, identityTracker.getAddresses(mysterIdentity).length);
            Assertions.assertTrue(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.1")));
            Assertions.assertTrue(identityTracker.isUp(MysterAddress.createMysterAddress("222.1.2.3")));
        }
        
        @Test
        public void testNoCleanupOnEverythingDown() throws IOException, InterruptedException {
            Consumer<PingResponse> pingListener = (_) -> {
            };

            IdentityTracker identityTracker = new IdentityTracker(pinger, pingListener, (_)->{});     
            
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("10.10.10.2"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("10.10.10.3"));
            
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("12.1.2.3"));
            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("24.10.10.3"));
            
            TrackerUtils.INVOKER.waitForThread();
            
            MysterAddress[] addresses = identityTracker.getAddresses(mysterIdentity);
            Assertions.assertEquals(4, addresses.length);
            
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.2")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("24.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("12.1.2.3")));
            Set<MysterAddress> addressSet = new HashSet<MysterAddress>(Arrays.asList( addresses));
            Assertions.assertTrue(addressSet.contains(MysterAddress.createMysterAddress("10.10.10.2")));
            Assertions.assertTrue(addressSet.contains(MysterAddress.createMysterAddress("10.10.10.3")));
            Assertions.assertTrue(addressSet.contains(MysterAddress.createMysterAddress("24.10.10.3")));
            Assertions.assertTrue(addressSet.contains(MysterAddress.createMysterAddress("12.1.2.3")));
            
            identityTracker.cleanUpOldAddresses(mysterIdentity);
            Assertions.assertEquals(4, identityTracker.getAddresses(mysterIdentity).length);
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.2")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("10.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("24.10.10.3")));
            Assertions.assertFalse(identityTracker.isUp(MysterAddress.createMysterAddress("12.1.2.3")));
            
            Set<MysterAddress> addressSet2 = new HashSet<MysterAddress>(Arrays.asList( identityTracker.getAddresses(mysterIdentity)));
            Assertions.assertTrue(addressSet2.contains(MysterAddress.createMysterAddress("10.10.10.2")));
            Assertions.assertTrue(addressSet2.contains(MysterAddress.createMysterAddress("10.10.10.3")));
            Assertions.assertTrue(addressSet2.contains(MysterAddress.createMysterAddress("24.10.10.3")));
            Assertions.assertTrue(addressSet2.contains(MysterAddress.createMysterAddress("12.1.2.3")));
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
        public void testBasicPingEvents() throws IOException, InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<PingResponse> pingListener = (r) -> {
                counter.incrementAndGet();
                latch.countDown();
            };

            IdentityTracker identityTracker = new IdentityTracker(pinger, pingListener, (_)->{});

            identityTracker.addIdentity(mysterIdentity, MysterAddress.createMysterAddress("11.10.10.1"));

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
