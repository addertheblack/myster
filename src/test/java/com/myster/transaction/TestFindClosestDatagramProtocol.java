package com.myster.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.client.FindClosestDatagramClient;
import com.myster.net.server.datagram.FindClosestDatagramServer;
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.tracker.IdentityNeighborSet;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;

class TestFindClosestDatagramProtocol {
    private static KeyPair exactKey;
    private static KeyPair leftKey;
    private static KeyPair rightKey;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        exactKey = generator.generateKeyPair();
        leftKey = generator.generateKeyPair();
        rightKey = generator.generateKeyPair();
    }

    @Test
    void roundTripPreservesSparseDirectionalGroupsAndDerivesCids() throws Exception {
        PublicKeyIdentity exact = new PublicKeyIdentity(exactKey.getPublic());
        PublicKeyIdentity left = new PublicKeyIdentity(leftKey.getPublic());
        MysterAddress exactAddress = MysterAddress.createMysterAddress("127.0.0.1:7000");
        MysterAddress leftAddress = MysterAddress.createMysterAddress("127.0.0.2:7001");
        MysterServerPool pool = poolWith(new IdentityNeighborSet(Optional.of(exact),
                                                                 List.of(left),
                                                                 List.of()),
                                                exact, exactAddress,
                                                left, leftAddress);
        FindClosestDatagramClient client = new FindClosestDatagramClient(cid(9), 2);
        Transaction request = request(client.getDataForOutgoingPacket());
        List<Transaction> replies = new ArrayList<>();

        new FindClosestDatagramServer(pool).transactionReceived(replies::add, request, null);

        assertEquals(1, replies.size());
        assertEquals(DatagramConstants.NO_ERROR, replies.getFirst().getErrorCode());
        ThreeDnsAddressCandidateSet decoded = client.getObjectFromTransaction(replies.getFirst());
        assertEquals(exact, decoded.exact().orElseThrow().identity());
        assertEquals(ServerCid.fromPublicKey(exactKey.getPublic()), decoded.exact().orElseThrow().cid());
        assertEquals(exactAddress, decoded.exact().orElseThrow().address());
        assertEquals(List.of(left), decoded.left().stream().map(c -> c.identity()).toList());
        assertEquals(leftAddress, decoded.left().getFirst().address());
        assertTrue(decoded.right().isEmpty());
    }

    @Test
    void serverDefaultsAndClampsPerSideLimit() throws Exception {
        AtomicInteger observedLimit = new AtomicInteger();
        MysterServerPool pool = mock(MysterServerPool.class);
        when(pool.findClosestByCid(org.mockito.ArgumentMatchers.any(),
                                   org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    observedLimit.set(invocation.getArgument(1));
                    return IdentityNeighborSet.empty();
                });
        FindClosestDatagramServer server = new FindClosestDatagramServer(pool);

        server.transactionReceived(_ -> {}, request(request(cid(1), Optional.empty())), null);
        assertEquals(ThreeDnsAddressCandidateSet.DEFAULT_PER_SIDE_LIMIT, observedLimit.get());

        server.transactionReceived(_ -> {}, request(request(cid(1), Optional.of(99))), null);
        assertEquals(ThreeDnsAddressCandidateSet.MAX_PER_SIDE_LIMIT, observedLimit.get());
    }

    @Test
    void serverOmitsCandidateThatIsNoLongerUpWhenEncoding() throws Exception {
        PublicKeyIdentity right = new PublicKeyIdentity(rightKey.getPublic());
        MysterServerPool pool = mock(MysterServerPool.class);
        when(pool.findClosestByCid(org.mockito.ArgumentMatchers.any(),
                                   org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new IdentityNeighborSet(Optional.empty(), List.of(), List.of(right)));
        MysterServer downServer = mock(MysterServer.class);
        when(downServer.getStatus()).thenReturn(false);
        when(pool.getCachedMysterServer(right)).thenReturn(Optional.of(downServer));
        FindClosestDatagramClient client = new FindClosestDatagramClient(cid(4), 2);
        List<Transaction> replies = new ArrayList<>();

        new FindClosestDatagramServer(pool).transactionReceived(replies::add,
                                                                 request(client.getDataForOutgoingPacket()),
                                                                 null);

        assertTrue(client.getObjectFromTransaction(replies.getFirst()).right().isEmpty());
    }

    @Test
    void malformedTargetAndOversizedResponseAreRejected() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        FindClosestDatagramServer server = new FindClosestDatagramServer(pool);
        MessagePak malformed = MessagePak.newEmpty();
        malformed.putInt("/schemaVersion", ThreeDnsAddressCandidateSet.SCHEMA_VERSION);
        malformed.putByteArray("/targetCid", new byte[15]);

        assertThrows(BadPacketException.class,
                     () -> server.transactionReceived(_ -> {},
                                                       request(malformed.toBytes()),
                                                       null));

        FindClosestDatagramClient client = new FindClosestDatagramClient(cid(2), 2);
        byte[] oversized = new byte[ThreeDnsAddressCandidateSet.MAX_RESPONSE_BYTES + 1];
        assertThrows(java.io.IOException.class,
                     () -> client.getObjectFromTransaction(reply(oversized)));
    }

    @Test
    void serverRejectsResponseThatExceedsByteBudget() throws Exception {
        PublicKey hugeKey = mock(PublicKey.class);
        when(hugeKey.getEncoded()).thenReturn(new byte[3_000]);
        PublicKeyIdentity identity = new PublicKeyIdentity(hugeKey);
        List<PublicKeyIdentity> four = List.of(identity, identity, identity, identity);
        MysterServerPool pool = mock(MysterServerPool.class);
        when(pool.findClosestByCid(org.mockito.ArgumentMatchers.any(),
                                   org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new IdentityNeighborSet(Optional.empty(), four, four));
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7000");
        MysterServer server = upServer(identity, address);
        when(pool.getCachedMysterServer(identity)).thenReturn(Optional.of(server));
        FindClosestDatagramClient client = new FindClosestDatagramClient(cid(5), 4);

        assertThrows(BadPacketException.class,
                     () -> new FindClosestDatagramServer(pool).transactionReceived(
                             _ -> {}, request(client.getDataForOutgoingPacket()), null));
    }

    @Test
    void invalidCandidatePortRejectsWholeResponse() throws Exception {
        FindClosestDatagramClient client = new FindClosestDatagramClient(cid(3), 2);
        MessagePak response = MessagePak.newEmpty();
        response.putInt("/schemaVersion", ThreeDnsAddressCandidateSet.SCHEMA_VERSION);
        response.putInt("/exactCount", 0);
        response.putInt("/leftCount", 1);
        response.putByteArray("/left/0/publicKey", leftKey.getPublic().getEncoded());
        response.putString("/left/0/ip", "127.0.0.1");
        response.putInt("/left/0/port", 0);
        response.putInt("/rightCount", 0);

        assertThrows(java.io.IOException.class,
                     () -> client.getObjectFromTransaction(reply(response.toBytes())));

        response.putInt("/left/0/port", 6669);
        response.putString("/left/0/ip", "example.com");
        assertThrows(java.io.IOException.class,
                     () -> client.getObjectFromTransaction(reply(response.toBytes())));
    }

    private static MysterServerPool poolWith(IdentityNeighborSet neighbors,
                                             PublicKeyIdentity exact,
                                             MysterAddress exactAddress,
                                             PublicKeyIdentity left,
                                             MysterAddress leftAddress) {
        MysterServerPool pool = mock(MysterServerPool.class);
        when(pool.findClosestByCid(org.mockito.ArgumentMatchers.any(),
                                   org.mockito.ArgumentMatchers.anyInt())).thenReturn(neighbors);
        MysterServer exactServer = upServer(exact, exactAddress);
        MysterServer leftServer = upServer(left, leftAddress);
        when(pool.getCachedMysterServer(exact)).thenReturn(Optional.of(exactServer));
        when(pool.getCachedMysterServer(left)).thenReturn(Optional.of(leftServer));
        return pool;
    }

    private static MysterServer upServer(PublicKeyIdentity identity, MysterAddress address) {
        MysterServer server = mock(MysterServer.class);
        when(server.getIdentity()).thenReturn(identity);
        when(server.getStatus()).thenReturn(true);
        when(server.getUpAddresses()).thenReturn(new MysterAddress[] { address });
        when(server.getBestAddress()).thenReturn(Optional.of(address));
        return server;
    }

    private static byte[] request(ServerCid target, Optional<Integer> limit) throws Exception {
        MessagePak request = MessagePak.newEmpty();
        request.putInt("/schemaVersion", ThreeDnsAddressCandidateSet.SCHEMA_VERSION);
        request.putByteArray("/targetCid", target.bytes());
        limit.ifPresent(value -> request.putInt("/perSideLimit", value));
        return request.toBytes();
    }

    private static Transaction request(byte[] payload) throws Exception {
        return new Transaction(MysterAddress.createMysterAddress("127.0.0.1:6669"),
                               DatagramConstants.THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE,
                               1,
                               payload);
    }

    private static Transaction reply(byte[] payload) throws Exception {
        return new Transaction(request(new byte[0]), payload, DatagramConstants.NO_ERROR);
    }

    private static ServerCid cid(int lastByte) {
        byte[] bytes = new byte[ServerCid.LENGTH];
        bytes[bytes.length - 1] = (byte) lastByte;
        return new ServerCid(bytes);
    }
}
