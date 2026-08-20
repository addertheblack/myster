package com.myster.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.general.util.MapPreferences;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

class TestTrackerThreeDnsCandidates {
    @Test
    void snapshotUsesLivePoolWithoutLocalThreeDnsList() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        TypeDescriptionList types = mock(TypeDescriptionList.class);
        when(types.getEnabledTypes()).thenReturn(new TypeDescription[0]);
        PublicKeyIdentity exactIdentity = identity(1);
        PublicKeyIdentity leftIdentity = identity(2);
        PublicKeyIdentity skippedIdentity = identity(3);
        PublicKeyIdentity rightIdentity = identity(4);
        ServerCid target = ServerCid.fromPublicKey(exactIdentity.getPublicKey());
        when(pool.findClosestByCid(target, 2)).thenReturn(new IdentityNeighborSet(
                Optional.of(exactIdentity),
                List.of(leftIdentity, skippedIdentity),
                List.of(rightIdentity)));

        MysterAddress exactAddress = address(1, 7001);
        MysterAddress leftFallback = address(2, 7002);
        MysterAddress leftBest = address(3, 7003);
        MysterAddress rightAddress = address(4, 7004);
        MysterServer exactServer = server(exactAddress, exactAddress);
        MysterServer leftServer = server(leftFallback, leftBest);
        MysterServer rightServer = server(rightAddress, rightAddress);
        when(pool.getCachedMysterServer(exactIdentity)).thenReturn(Optional.of(exactServer));
        when(pool.getCachedMysterServer(leftIdentity)).thenReturn(Optional.of(leftServer));
        when(pool.getCachedMysterServer(skippedIdentity)).thenReturn(Optional.empty());
        when(pool.getCachedMysterServer(rightIdentity)).thenReturn(Optional.of(rightServer));

        Tracker tracker = new Tracker(pool,
                                      new MapPreferences(),
                                      types,
                                      Optional.empty());
        ThreeDnsAddressCandidateSet result =
                tracker.getThreeDnsCandidatesForTarget(target, 2);

        verify(pool).findClosestByCid(target, 2);
        assertEquals(exactAddress, result.exact().orElseThrow().address());
        assertEquals(List.of(leftBest), result.left().stream().map(c -> c.address()).toList());
        assertEquals(List.of(rightAddress), result.right().stream().map(c -> c.address()).toList());
        assertThrows(UnsupportedOperationException.class,
                     () -> result.left().add(result.exact().orElseThrow()));
    }

    private static MysterServer server(MysterAddress first, MysterAddress best) {
        MysterServer server = mock(MysterServer.class);
        when(server.isUp()).thenReturn(true);
        when(server.getUpAddresses()).thenReturn(new MysterAddress[] { first, best });
        when(server.getBestAddress()).thenReturn(Optional.of(best));
        return server;
    }

    private static PublicKeyIdentity identity(int value) {
        return new PublicKeyIdentity(new TestPublicKey(new byte[] { (byte) value }));
    }

    private static MysterAddress address(int lastOctet, int port) throws Exception {
        return new MysterAddress(InetAddress.getByName("127.0.0." + lastOctet), port);
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
