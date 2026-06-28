package com.myster.threedns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.general.util.MapPreferences;
import com.myster.identity.Cid128;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.client.PingResponse;
import com.myster.tracker.ExternalName;
import com.myster.tracker.IdentityNeighborSet;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterPoolListener;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;

class TestThreeDnsServerList {
    @Test
    void seedsFromPublicKeyServersOnly() throws Exception {
        FakePool pool = new FakePool();
        ThreeDnsServerList list =
                new ThreeDnsServerList(cid(0, 0), pool, new MapPreferences(), () -> {});
        FakeServer publicKeyServer = pool.addPublicServer(1, true);
        FakeServer addressOnlyServer = pool.addAddressOnlyServer(2, true);

        list.consider(publicKeyServer);
        list.consider(addressOnlyServer);

        assertEquals(List.of(publicKeyServer.identity), list.seeds(10));
        assertFalse(list.snapshot().stream()
                .anyMatch(entry -> entry.server().getIdentity().equals(addressOnlyServer.getIdentity())));
    }

    @Test
    void retainedEntriesAreBalancedByTargetAndSide() throws Exception {
        FakePool pool = new FakePool();
        ThreeDnsServerList list =
                new ThreeDnsServerList(cid(0, 0), pool, new MapPreferences(), () -> {});

        for (int i = 0; i < 6; i++) {
            list.consider(pool.addPublicServer(10 + i, true));
        }

        Map<String, Long> counts = list.snapshot().stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.targetCid() + ":" + entry.side(),
                    Collectors.counting()));

        assertFalse(counts.isEmpty());
        assertTrue(counts.values().stream().allMatch(count -> count <= ThreeDnsServerList.DEFAULT_PER_SIDE_LIMIT));
    }

    @Test
    void downRetainedEntriesAreRemovedAndRefilledFromPool() throws Exception {
        FakePool pool = new FakePool();
        ThreeDnsServerList list =
                new ThreeDnsServerList(cid(0, 0), pool, new MapPreferences(), () -> {});
        FakeServer first = pool.addPublicServer(20, true);
        FakeServer second = pool.addPublicServer(21, true);
        FakeServer replacement = pool.addPublicServer(22, true);

        list.consider(first);
        list.consider(second);
        assertTrue(containsIdentity(list.snapshot(), first.identity));

        first.up = false;
        list.serverPing(first.address, false);

        assertFalse(containsIdentity(list.snapshot(), first.identity));
        assertTrue(containsIdentity(list.snapshot(), replacement.identity));
    }

    @Test
    void persistedEntriesRestoreEvenWhenCurrentlyDown() throws Exception {
        FakePool pool = new FakePool();
        MapPreferences preferences = new MapPreferences();
        AtomicInteger changes = new AtomicInteger();
        FakeServer server = pool.addPublicServer(30, true);

        ThreeDnsServerList list = new ThreeDnsServerList(cid(0, 0), pool, preferences, changes::incrementAndGet);
        list.consider(server);
        int retainedCount = list.snapshot().size();
        assertTrue(retainedCount > 0);

        server.up = false;
        ThreeDnsServerList restored = new ThreeDnsServerList(cid(0, 0), pool, preferences, () -> {});

        assertEquals(retainedCount, restored.snapshot().size());
        assertTrue(restored.seeds(10).isEmpty());
    }

    private static boolean containsIdentity(List<ThreeDnsFingerEntry> entries, MysterIdentity identity) {
        return entries.stream().anyMatch(entry -> entry.server().getIdentity().equals(identity));
    }

    private static Cid128 cid(long hi, long lo) {
        byte[] bytes = new byte[Cid128.LENGTH];
        writeLong(bytes, 0, hi);
        writeLong(bytes, Long.BYTES, lo);
        return new Cid128(bytes);
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            bytes[offset + i] = (byte) value;
            value >>>= Byte.SIZE;
        }
    }

    private static Cid128 cid(PublicKeyIdentity identity) {
        return com.myster.identity.Util.generateCid(identity.getPublicKey());
    }

    private static final class FakePool implements MysterServerPool {
        private final Map<MysterIdentity, FakeServer> byIdentity = new LinkedHashMap<>();
        private final Map<MysterAddress, FakeServer> byAddress = new HashMap<>();
        private final Map<ExternalName, MysterIdentity> byExternalName = new HashMap<>();

        FakeServer addPublicServer(int seed, boolean up) throws UnknownHostException {
            PublicKeyIdentity identity = new PublicKeyIdentity(new FakePublicKey(seed));
            return addServer(identity, seed, up);
        }

        FakeServer addAddressOnlyServer(int seed, boolean up) throws UnknownHostException {
            return addServer(new FakeAddressIdentity("address-" + seed), seed, up);
        }

        private FakeServer addServer(MysterIdentity identity, int seed, boolean up)
                throws UnknownHostException {
            FakeServer server = new FakeServer(identity,
                                               new ExternalName("server-" + seed),
                                               MysterAddress.createMysterAddress("24.30.40." + seed),
                                               up);
            byIdentity.put(identity, server);
            byAddress.put(server.address, server);
            byExternalName.put(server.externalName, identity);
            return server;
        }

        @Override
        public void suggestAddress(String address) {}

        @Override
        public Optional<MysterIdentity> lookupIdentityFromName(ExternalName externalName) {
            return Optional.ofNullable(byExternalName.get(externalName));
        }

        @Override
        public Optional<PublicKey> lookupIdentityFromCid(Cid128 cid) {
            return byIdentity.keySet().stream()
                    .filter(PublicKeyIdentity.class::isInstance)
                    .map(PublicKeyIdentity.class::cast)
                    .filter(identity -> TestThreeDnsServerList.cid(identity).equals(cid))
                    .map(PublicKeyIdentity::getPublicKey)
                    .findFirst();
        }

        @Override
        public IdentityNeighborSet findClosestByCid(Cid128 target, int perSideLimit) {
            int limit = perSideLimit <= 0 ? ThreeDnsServerList.DEFAULT_PER_SIDE_LIMIT : perSideLimit;
            List<PublicKeyIdentity> usable = byIdentity.values().stream()
                    .filter(FakeServer::getStatus)
                    .map(FakeServer::getIdentity)
                    .filter(PublicKeyIdentity.class::isInstance)
                    .map(PublicKeyIdentity.class::cast)
                    .toList();

            Optional<PublicKeyIdentity> exact = usable.stream()
                    .filter(identity -> TestThreeDnsServerList.cid(identity).equals(target))
                    .findFirst();

            List<PublicKeyIdentity> left = usable.stream()
                    .filter(identity -> exact.filter(identity::equals).isEmpty())
                    .sorted(Comparator.comparing(TestThreeDnsServerList::cid,
                                                 target::comparePredecessorDistance))
                    .limit(limit)
                    .toList();
            List<PublicKeyIdentity> right = usable.stream()
                    .filter(identity -> exact.filter(identity::equals).isEmpty())
                    .sorted(Comparator.comparing(TestThreeDnsServerList::cid,
                                                 target::compareSuccessorDistance))
                    .limit(limit)
                    .toList();

            return new IdentityNeighborSet(exact, left, right);
        }

        @Override
        public Optional<MysterServer> getCachedMysterServer(MysterIdentity identity) {
            return Optional.ofNullable(byIdentity.get(identity));
        }

        @Override
        public Optional<MysterServer> getCachedMysterServer(MysterAddress address) {
            return Optional.ofNullable(byAddress.get(address));
        }

        @Override
        public boolean existsInPool(MysterIdentity identity) {
            return byIdentity.containsKey(identity);
        }

        @Override
        public boolean existsInPool(MysterAddress address) {
            return byAddress.containsKey(address);
        }

        @Override
        public void addPoolListener(MysterPoolListener listener) {}

        @Override
        public void removePoolListener(MysterPoolListener listener) {}

        @Override
        public void clearHardLinks() {}

        @Override
        public void forEach(Consumer<MysterServer> consumer) {
            byIdentity.values().forEach(consumer);
        }

        @Override
        public void suggestAddress(MysterAddress address) {}

        @Override
        public void receivedDownNotification(MysterAddress address) {}
    }

    private static final class FakeServer implements MysterServer {
        private final MysterIdentity identity;
        private final ExternalName externalName;
        private final MysterAddress address;
        private boolean up;

        private FakeServer(MysterIdentity identity, ExternalName externalName, MysterAddress address, boolean up) {
            this.identity = identity;
            this.externalName = externalName;
            this.address = address;
            this.up = up;
        }

        @Override
        public boolean getStatus() {
            return up;
        }

        @Override
        public Optional<MysterAddress> getBestAddress() {
            return Optional.of(address);
        }

        @Override
        public MysterAddress[] getAddresses() {
            return new MysterAddress[] { address };
        }

        @Override
        public MysterAddress[] getUpAddresses() {
            return up ? new MysterAddress[] { address } : new MysterAddress[] {};
        }

        @Override
        public int getNumberOfFiles(MysterType type) {
            return 0;
        }

        @Override
        public boolean knowsAboutType(MysterType type) {
            return false;
        }

        @Override
        public int getTotalNumberOfFiles() {
            return 0;
        }

        @Override
        public double getSpeed() {
            return 0;
        }

        @Override
        public double getRank(MysterType type) {
            return 0;
        }

        @Override
        public String getServerName() {
            return externalName.toString();
        }

        @Override
        public int getPingTime() {
            return up ? 1 : DOWN;
        }

        @Override
        public boolean isUntried() {
            return false;
        }

        @Override
        public long getUptime() {
            return 0;
        }

        @Override
        public MysterIdentity getIdentity() {
            return identity;
        }

        @Override
        public ExternalName getExternalName() {
            return externalName;
        }

        @Override
        public void tryPingAgain(MysterAddress address) {}
    }

    private static final class FakeAddressIdentity implements MysterIdentity {
        private final String value;

        private FakeAddressIdentity(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof FakeAddressIdentity other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static final class FakePublicKey implements PublicKey {
        private final byte[] encoded;

        private FakePublicKey(int seed) {
            encoded = new byte[32];
            for (int i = 0; i < encoded.length; i++) {
                encoded[i] = (byte) (seed + i);
            }
        }

        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }
    }
}
