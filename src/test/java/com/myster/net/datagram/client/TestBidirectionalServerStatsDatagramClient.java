package com.myster.net.datagram.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.DataPacket;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.ServerStats;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.type.MysterType;

class TestBidirectionalServerStatsDatagramClient {
    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void fullCardContainsLocalStatsAndUsesTransaction102() throws Exception {
        BidirectionalServerStatsDatagramClient client = newClient(initializedFileManager());

        MessagePak card = decode(client.getDataForOutgoingPacket());

        assertEquals(DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE,
                     client.getCode());
        assertEquals(7001, card.getInt(ServerStats.PORT).orElseThrow());
        assertEquals("client", card.getString(ServerStats.SERVER_NAME).orElseThrow());
        assertArrayEquals(keyPair.getPublic().getEncoded(),
                          card.getByteArray(ServerStats.IDENTITY).orElseThrow());
        assertTrue(card.getString(ServerStats.MYSTER_VERSION).isPresent());
    }

    @Test
    void uninitializedFileManagerIsIllegalForTransaction102() {
        MysterType type = new MysterType(new byte[16]);
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[] { type });
        when(fileManager.hasInitialized(type)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                     () -> newClient(fileManager).getDataForOutgoingPacket());
    }

    @Test
    void buildFailureDoesNotProduceAnEmptyPacket() {
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenThrow(new IllegalStateException("broken"));

        assertThrows(IllegalStateException.class,
                     () -> newClient(fileManager).getDataForOutgoingPacket());
    }

    @Test
    void wrapperPropagatesUnexpectedInitializationFailure() throws Exception {
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenThrow(new IllegalStateException("broken"));
        PublicKeyLookup lookup = mock(PublicKeyLookup.class);
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7002");
        when(lookup.getCached(address)).thenReturn(Optional.empty());
        TransactionManager transactionManager = mock(TransactionManager.class);
        MysterDatagramImpl datagram = new MysterDatagramImpl(
                transactionManager,
                mock(UDPPingClient.class),
                lookup,
                () -> "client",
                () -> 7001,
                mock(Identity.class),
                fileManager);

        assertThrows(IllegalStateException.class,
                () -> datagram.getBidirectionalServerStats(new ParamBuilder(address)));

        org.mockito.Mockito.verifyNoInteractions(transactionManager);
    }

    @Test
    void datagramWrapperUses101UntilLocalStatsInitializeThenUses102() throws Exception {
        MysterType type = new MysterType(new byte[16]);
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[] { type });
        when(fileManager.hasInitialized(type)).thenReturn(false, true);
        PublicKeyLookup lookup = mock(PublicKeyLookup.class);
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7002");
        when(lookup.getCached(address)).thenReturn(Optional.empty());
        AtomicInteger transactionCode = new AtomicInteger();
        TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.sendTransaction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    transactionCode.set(invocation.getArgument(1));
                    return 1;
                });
        Identity identity = mock(Identity.class);
        when(identity.getMainIdentity()).thenReturn(Optional.of(keyPair));
        MysterDatagramImpl datagram = new MysterDatagramImpl(
                transactionManager,
                mock(UDPPingClient.class),
                lookup,
                () -> "client",
                () -> 7001,
                identity,
                fileManager);

        datagram.getBidirectionalServerStats(new ParamBuilder(address));
        assertEquals(DatagramConstants.SERVER_STATS_TRANSACTION_CODE, transactionCode.get());

        datagram.getBidirectionalServerStats(new ParamBuilder(address));
        assertEquals(DatagramConstants.BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE,
                     transactionCode.get());
        verify(transactionManager, org.mockito.Mockito.times(2)).sendTransaction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preInitialization101PreservesExpectedKeyEncryption() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair serverKeyPair = generator.generateKeyPair();
        MysterType type = new MysterType(new byte[16]);
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[] { type });
        when(fileManager.hasInitialized(type)).thenReturn(false);
        TransactionManager transactionManager = mock(TransactionManager.class);
        org.mockito.ArgumentCaptor<DataPacket> packet =
                org.mockito.ArgumentCaptor.forClass(DataPacket.class);
        AtomicInteger transactionCode = new AtomicInteger();
        when(transactionManager.sendTransaction(
                packet.capture(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    transactionCode.set(invocation.getArgument(1));
                    return 1;
                });
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7002");
        MysterDatagramImpl datagram = new MysterDatagramImpl(
                transactionManager,
                mock(UDPPingClient.class),
                mock(PublicKeyLookup.class),
                () -> "client",
                () -> 7001,
                null,
                fileManager);

        datagram.getBidirectionalServerStats(new ParamBuilder(address)
                .withExpectedServerPublicKey(serverKeyPair.getPublic()));

        assertEquals(DatagramConstants.STLS_CODE, transactionCode.get());
        DatagramEncryptUtil.R decrypted = DatagramEncryptUtil.decryptRequestPacket(
                packet.getValue().getData(), keyLookup(serverKeyPair));
        ByteBuffer innerPayload = ByteBuffer.wrap(decrypted.payload);
        assertEquals(DatagramConstants.SERVER_STATS_TRANSACTION_CODE,
                     innerPayload.getInt());
        assertEquals(0, innerPayload.remaining());
    }

    @Test
    void responseParserReturnsRemoteStatsAndRejectsMalformedData() throws Exception {
        BidirectionalServerStatsDatagramClient client = newClient(initializedFileManager());
        MessagePak response = MessagePak.newEmpty();
        response.putString(ServerStats.SERVER_NAME, "remote");
        response.putInt(ServerStats.PORT, 7002);
        Transaction transaction = mock(Transaction.class);
        when(transaction.getData()).thenReturn(encode(response));

        MessagePak parsed = client.getObjectFromTransaction(transaction);

        assertEquals("remote", parsed.getString(ServerStats.SERVER_NAME).orElseThrow());
        assertEquals(7002, parsed.getInt(ServerStats.PORT).orElseThrow());

        when(transaction.getData()).thenReturn(new byte[] { 0, 0, 0, 10, 1 });
        assertThrows(java.io.IOException.class,
                     () -> client.getObjectFromTransaction(transaction));
    }

    private static BidirectionalServerStatsDatagramClient newClient(
            FileTypeListManager fileManager) {
        Identity identity = mock(Identity.class);
        when(identity.getMainIdentity()).thenReturn(Optional.of(keyPair));
        return new BidirectionalServerStatsDatagramClient(
                "client", 7001, identity, fileManager);
    }

    private static FileTypeListManager initializedFileManager() {
        FileTypeListManager fileManager = mock(FileTypeListManager.class);
        when(fileManager.getFileTypeListing()).thenReturn(new MysterType[0]);
        return fileManager;
    }

    private static MessagePak decode(byte[] bytes) throws Exception {
        try (var in = new MysterDataInputStream(new ByteArrayInputStream(bytes))) {
            return in.readMessagePack();
        }
    }

    private static byte[] encode(MessagePak messagePak) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(bytes)) {
            out.writeMessagePack(messagePak);
        }
        return bytes.toByteArray();
    }

    private static DatagramEncryptUtil.Lookup keyLookup(KeyPair keyPair) {
        return new DatagramEncryptUtil.Lookup() {
            @Override
            public Optional<PublicKey> findPublicKey(ServerCid serverCid) {
                return Optional.empty();
            }

            @Override
            public Optional<KeyPair> getServerKeyPair(Object serverId) {
                return Optional.of(keyPair);
            }
        };
    }
}
