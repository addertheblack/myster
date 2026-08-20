package com.myster.threedns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

class TestThreeDnsLookupFrontier {
    @Test
    void retainsOnlyClosestBoundedCandidates() throws Exception {
        ServerCid target = cid(0);
        List<ThreeDnsAddressCandidate> candidates = candidates(70);
        List<ThreeDnsAddressCandidate> expected = candidates.stream()
                .sorted(byDistance(target))
                .limit(64)
                .toList();
        ThreeDnsLookupFrontier frontier = new ThreeDnsLookupFrontier(target, 64, 2);

        candidates.forEach(frontier::add);

        assertEquals(64, frontier.queuedCount());
        List<ThreeDnsAddressCandidate> actual = new ArrayList<>();
        Optional<ThreeDnsAddressCandidate> next;
        while ((next = frontier.pollEligible(Optional.empty())).isPresent()) {
            actual.add(next.get());
        }
        assertEquals(expected, actual);
    }

    @Test
    void acceptsOnlyStrictProgressAfterVerification() throws Exception {
        ServerCid target = cid(0);
        List<ThreeDnsAddressCandidate> ordered = candidates(9).stream()
                .sorted(byDistance(target))
                .toList();
        ThreeDnsAddressCandidate baseline = ordered.get(4);
        ThreeDnsLookupFrontier frontier = new ThreeDnsLookupFrontier(target, 20, 2);
        ordered.forEach(frontier::add);

        List<ThreeDnsAddressCandidate> actual = new ArrayList<>();
        Optional<ThreeDnsAddressCandidate> next;
        while ((next = frontier.pollEligible(Optional.of(baseline.cid()))).isPresent()) {
            actual.add(next.get());
        }

        assertEquals(ordered.subList(0, 4), actual);
    }

    @Test
    void boundsAlternateAddressesAndUsesOneAfterFailure() throws Exception {
        ServerCid target = cid(0);
        PublicKeyIdentity identity = identity(100);
        ThreeDnsAddressCandidate first = candidate(identity, 1, 7001);
        ThreeDnsAddressCandidate second = candidate(identity, 2, 7002);
        ThreeDnsAddressCandidate rejected = candidate(identity, 3, 7003);
        ThreeDnsLookupFrontier frontier = new ThreeDnsLookupFrontier(target, 10, 2);

        assertTrue(frontier.add(first));
        assertTrue(frontier.add(second));
        assertFalse(frontier.add(rejected));
        ThreeDnsAddressCandidate launched = frontier.pollEligible(Optional.empty()).orElseThrow();
        frontier.markLaunched(launched);
        assertTrue(frontier.pollEligible(Optional.empty()).isEmpty());
        frontier.markFailed(launched);

        assertTrue(frontier.pollEligible(Optional.empty()).isPresent());
    }

    @Test
    void allowsAnAddressSharedByDifferentIdentities() throws Exception {
        ThreeDnsLookupFrontier frontier = new ThreeDnsLookupFrontier(cid(0), 10, 2);
        MysterAddress address = new MysterAddress(InetAddress.getByName("127.0.0.80"), 7080);

        assertTrue(frontier.add(new ThreeDnsAddressCandidate(identity(200), address)));
        assertTrue(frontier.add(new ThreeDnsAddressCandidate(identity(201), address)));
        assertEquals(2, frontier.queuedCount());
    }

    private static Comparator<ThreeDnsAddressCandidate> byDistance(ServerCid target) {
        return (first, second) -> {
            int distance = target.comparePredecessorDistance(first.cid(), second.cid());
            if (distance != 0) {
                return distance;
            }
            return Arrays.compareUnsigned(first.identity().getPublicKey().getEncoded(),
                                          second.identity().getPublicKey().getEncoded());
        };
    }

    private static List<ThreeDnsAddressCandidate> candidates(int count) throws Exception {
        List<ThreeDnsAddressCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            candidates.add(candidate(identity(index), (index % 250) + 1, 7000 + index));
        }
        return candidates;
    }

    private static ThreeDnsAddressCandidate candidate(PublicKeyIdentity identity,
                                                       int lastOctet,
                                                       int port) throws Exception {
        return new ThreeDnsAddressCandidate(
                identity,
                new MysterAddress(InetAddress.getByName("127.0.0." + lastOctet), port));
    }

    private static PublicKeyIdentity identity(int value) {
        return new PublicKeyIdentity(new TestPublicKey(new byte[] {
                (byte) (value >>> 8), (byte) value }));
    }

    private static ServerCid cid(int value) {
        byte[] bytes = new byte[ServerCid.LENGTH];
        bytes[bytes.length - 1] = (byte) value;
        return new ServerCid(bytes);
    }

    private static final class TestPublicKey implements PublicKey {
        private final byte[] encoded;

        private TestPublicKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "test";
        }

        @Override
        public String getFormat() {
            return "test";
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestPublicKey other && Arrays.equals(encoded, other.encoded);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(encoded);
        }
    }
}
