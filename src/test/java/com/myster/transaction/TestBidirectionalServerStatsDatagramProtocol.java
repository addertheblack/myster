package com.myster.transaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.net.datagram.client.BidirectionalServerStatsDatagramClient;
import com.myster.net.datagram.client.EncryptingStandardDatagramClientImpl;
import com.myster.net.server.datagram.BidirectionalServerStatsDatagramServer;
import com.myster.net.server.datagram.EncryptedDatagramServer;
import com.myster.net.server.datagram.ServerStatsDatagramServer;
import com.myster.net.stream.server.ServerStats;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;

class TestBidirectionalServerStatsDatagramProtocol {
    private static KeyPair clientKeyPair;
    private static KeyPair serverKeyPair;

    @BeforeAll
    static void createKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        clientKeyPair = generator.generateKeyPair();
        serverKeyPair = generator.generateKeyPair();
    }

    @Test
    void roundTripReturnsResponderStatsAndSuggestsAdvertisedAddressIdentityPair()
            throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        BidirectionalServerStatsDatagramClient client = businessCardClient(
                clientIdentity(), initializedFileManager());
        Transaction request = request(client.getDataForOutgoingPacket());
        List<Transaction> replies = new ArrayList<>();

        businessCardServer(pool, serverIdentity(), initializedFileManager())
                .transactionReceived(replies::add, request, null);

        MysterAddress correctedAddress = MysterAddress.createMysterAddress("127.0.0.1:7001");
        PublicKeyIdentity clientIdentity = new PublicKeyIdentity(clientKeyPair.getPublic());
        verify(pool).suggestAddress(correctedAddress, clientIdentity);
        verify(pool, never()).suggestAddress(correctedAddress);
        assertEquals(1, replies.size());
        MessagePak response = client.getObjectFromTransaction(replies.getFirst());
        assertEquals("server", response.getString(ServerStats.SERVER_NAME).orElseThrow());
        assertEquals(7002, response.getInt(ServerStats.PORT).orElseThrow());
        assertArrayEquals(serverKeyPair.getPublic().getEncoded(),
                          response.getByteArray(ServerStats.IDENTITY).orElseThrow());
    }

    @Test
    void encryptedRoundTripPreservesBusinessCardPairAndResponse() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        BidirectionalServerStatsDatagramClient delegate = businessCardClient(
                clientIdentity(), initializedFileManager());
        EncryptingStandardDatagramClientImpl<MessagePak> encryptedClient =
                new EncryptingStandardDatagramClientImpl<>(
                        delegate, serverKeyPair.getPublic(), Optional.of(clientIdentity()));
        Transaction encryptedRequest = new Transaction(
                MysterAddress.createMysterAddress("127.0.0.1:54000"),
                DatagramConstants.STLS_CODE,
                1,
                encryptedClient.getDataForOutgoingPacket());
        List<Transaction> replies = new ArrayList<>();
        BidirectionalServerStatsDatagramServer businessServer = businessCardServer(
                pool, serverIdentity(), initializedFileManager());
        EncryptedDatagramServer encryptedServer = new EncryptedDatagramServer(
                (sender, transaction) -> businessServer.transactionReceived(
                        sender, transaction, null),
                keyLookup(serverKeyPair));

        encryptedServer.transactionReceived(replies::add, encryptedRequest, null);

        assertEquals(1, replies.size());
        assertEquals(DatagramConstants.STLS_CODE, replies.getFirst().getTransactionCode());
        MessagePak response = encryptedClient.getObjectFromTransaction(replies.getFirst());
        assertEquals("server", response.getString(ServerStats.SERVER_NAME).orElseThrow());
        verify(pool).suggestAddress(
                MysterAddress.createMysterAddress("127.0.0.1:7001"),
                new PublicKeyIdentity(clientKeyPair.getPublic()));
    }

    @Test
    void missingAndInvalidPortsNeverReachThePool() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        BidirectionalServerStatsDatagramServer server = businessCardServer(
                pool, serverIdentity(), initializedFileManager());

        assertThrows(BadPacketException.class,
                     () -> server.transactionReceived(_ -> {},
                             request(card(Optional.empty(), Optional.of(clientKeyPair.getPublic()))),
                             null));
        for (int invalidPort : new int[] { -1, 0, 65536 }) {
            assertThrows(BadPacketException.class,
                         () -> server.transactionReceived(_ -> {},
                                 request(card(Optional.of(invalidPort),
                                              Optional.of(clientKeyPair.getPublic()))),
                                 null));
        }

        verify(pool, never()).suggestAddress(
                org.mockito.ArgumentMatchers.any(MysterAddress.class));
        verify(pool, never()).suggestAddress(
                org.mockito.ArgumentMatchers.any(MysterAddress.class),
                org.mockito.ArgumentMatchers.any(PublicKeyIdentity.class));
    }

    @Test
    void malformedPayloadAndIdentityNeverReachThePool() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        BidirectionalServerStatsDatagramServer server = businessCardServer(
                pool, serverIdentity(), initializedFileManager());

        assertThrows(BadPacketException.class,
                     () -> server.transactionReceived(_ -> {},
                             request(new byte[] { 0, 0, 0, 20, 1 }), null));
        MessagePak invalidIdentity = MessagePak.newEmpty();
        invalidIdentity.putInt(ServerStats.PORT, 7001);
        invalidIdentity.putByteArray(ServerStats.IDENTITY, new byte[] { 1, 2, 3 });
        assertThrows(BadPacketException.class,
                     () -> server.transactionReceived(_ -> {},
                             request(encode(invalidIdentity)), null));

        verify(pool, never()).suggestAddress(
                org.mockito.ArgumentMatchers.any(MysterAddress.class));
        verify(pool, never()).suggestAddress(
                org.mockito.ArgumentMatchers.any(MysterAddress.class),
                org.mockito.ArgumentMatchers.any(PublicKeyIdentity.class));
    }

    @Test
    void authenticatedCallerCannotAdvertiseAnotherCid() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();
        Transaction request = request(card(Optional.of(7001),
                                           Optional.of(clientKeyPair.getPublic())))
                .withCallerCid(Optional.of(ServerCid.fromPublicKey(otherKeyPair.getPublic())));

        assertThrows(BadPacketException.class,
                     () -> businessCardServer(pool,
                                              serverIdentity(),
                                              initializedFileManager())
                             .transactionReceived(_ -> {}, request, null));
        verify(pool, never()).suggestAddress(
                org.mockito.ArgumentMatchers.any(MysterAddress.class),
                org.mockito.ArgumentMatchers.any(PublicKeyIdentity.class));
    }

    @Test
    void anonymousCardUsesAddressOnlySuggestion() throws Exception {
        MysterServerPool pool = mock(MysterServerPool.class);
        MysterAddress correctedAddress = MysterAddress.createMysterAddress("127.0.0.1:7001");

        businessCardServer(pool, serverIdentity(), initializedFileManager())
                .transactionReceived(_ -> {},
                        request(card(Optional.of(7001), Optional.empty())), null);

        verify(pool).suggestAddress(correctedAddress);
        verify(pool, never()).suggestAddress(
                eq(correctedAddress),
                org.mockito.ArgumentMatchers.any(PublicKeyIdentity.class));
    }

    @Test
    void minimalResponseRetainsResponderIdentityNameAndPort() throws Exception {
        MysterType type = new MysterType(new byte[16]);
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[] { type });
        when(fileManager.hasInitialized(type)).thenReturn(false);
        BidirectionalServerStatsDatagramClient client = businessCardClient(
                clientIdentity(), initializedFileManager());
        List<Transaction> replies = new ArrayList<>();

        businessCardServer(mock(MysterServerPool.class), serverIdentity(), fileManager)
                .transactionReceived(replies::add,
                                     request(client.getDataForOutgoingPacket()),
                                     null);

        MessagePak response = client.getObjectFromTransaction(replies.getFirst());
        assertEquals(7002, response.getInt(ServerStats.PORT).orElseThrow());
        assertEquals("server", response.getString(ServerStats.SERVER_NAME).orElseThrow());
        assertArrayEquals(serverKeyPair.getPublic().getEncoded(),
                          response.getByteArray(ServerStats.IDENTITY).orElseThrow());
    }

    @Test
    void legacyAndBidirectionalStatsCodesRemainIndependent() {
        ServerStatsDatagramServer legacy = new ServerStatsDatagramServer(
                () -> "server", () -> 7002, serverIdentity(), initializedFileManager());
        BidirectionalServerStatsDatagramServer bidirectional = businessCardServer(
                mock(MysterServerPool.class), serverIdentity(), initializedFileManager());

        assertEquals(DatagramConstants.SERVER_STATS_TRANSACTION_CODE,
                     legacy.getTransactionCode());
        assertEquals(DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE,
                     bidirectional.getTransactionCode());
        assertTrue(legacy.getTransactionCode() != bidirectional.getTransactionCode());
    }

    private static BidirectionalServerStatsDatagramClient businessCardClient(
            Identity identity,
            FileTypeListManager fileManager) {
        return new BidirectionalServerStatsDatagramClient(
                "client", 7001, identity, fileManager);
    }

    private static BidirectionalServerStatsDatagramServer businessCardServer(
            MysterServerPool pool,
            Identity identity,
            FileTypeListManager fileManager) {
        return new BidirectionalServerStatsDatagramServer(
                () -> "server", () -> 7002, identity, fileManager, pool);
    }

    private static Identity clientIdentity() {
        return identity(clientKeyPair);
    }

    private static Identity serverIdentity() {
        return identity(serverKeyPair);
    }

    private static Identity identity(KeyPair keyPair) {
        Identity identity = mock(Identity.class);
        when(identity.getMainIdentity()).thenReturn(Optional.of(keyPair));
        return identity;
    }

    private static FileTypeListManager initializedFileManager() {
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[0]);
        return fileManager;
    }

    private static byte[] card(Optional<Integer> port, Optional<PublicKey> publicKey)
            throws Exception {
        MessagePak card = MessagePak.newEmpty();
        port.ifPresent(value -> card.putInt(ServerStats.PORT, value));
        publicKey.ifPresent(value -> card.putByteArray(ServerStats.IDENTITY,
                                                       value.getEncoded()));
        return encode(card);
    }

    private static byte[] encode(MessagePak messagePak) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (var out = new com.myster.net.stream.client.MysterDataOutputStream(bytes)) {
            out.writeMessagePack(messagePak);
        }
        return bytes.toByteArray();
    }

    private static Transaction request(byte[] payload) throws Exception {
        return new Transaction(MysterAddress.createMysterAddress("127.0.0.1:54000"),
                               DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE,
                               1,
                               payload);
    }

    private static DatagramEncryptUtil.Lookup keyLookup(KeyPair keyPair) {
        return new DatagramEncryptUtil.Lookup() {
            @Override
            public Optional<PublicKey> findPublicKey(ServerCid serverCid) {
                if (serverCid.equals(ServerCid.fromPublicKey(clientKeyPair.getPublic()))) {
                    return Optional.of(clientKeyPair.getPublic());
                }
                return Optional.empty();
            }

            @Override
            public Optional<KeyPair> getServerKeyPair(Object serverId) {
                return Optional.of(keyPair);
            }
        };
    }
}
