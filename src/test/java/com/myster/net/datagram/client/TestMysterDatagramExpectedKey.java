package com.myster.net.datagram.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.myster.filemanager.FileTypeListManager;
import com.myster.cid.ServerCid;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.DataPacket;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.threedns.ThreeDnsAddressCandidate;
import com.myster.threedns.ThreeDnsPeerClient;
import com.myster.threedns.ThreeDnsVerifiedQueryResult;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;

class TestMysterDatagramExpectedKey {
    @Test
    void expectedKeyPreservesAddressOverridesCacheAndForcesEncryption() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair expectedKey = generator.generateKeyPair();
        KeyPair conflictingCachedKey = generator.generateKeyPair();
        MysterAddress address = MysterAddress.createMysterAddress("127.0.0.1:7000");
        ParamBuilder params = new ParamBuilder(address)
                .withExpectedServerPublicKey(expectedKey.getPublic());
        assertEquals(address, params.getAddress().orElseThrow());
        assertEquals(expectedKey.getPublic(), params.getExpectedServerPublicKey().orElseThrow());

        AtomicReference<DataPacket> sentPacket = new AtomicReference<>();
        AtomicInteger sentCode = new AtomicInteger();
        AtomicReference<TransactionListener> sentListener = new AtomicReference<>();
        TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.sendTransaction(any(), anyInt(), any(TransactionListener.class)))
                .thenAnswer(invocation -> {
                    sentPacket.set(invocation.getArgument(0));
                    sentCode.set(invocation.getArgument(1));
                    sentListener.set(invocation.getArgument(2));
                    return 1;
                });
        PublicKeyLookup lookup = new PublicKeyLookup() {
            @Override
            public Optional<PublicKey> convert(com.myster.tracker.MysterIdentity identity) {
                return Optional.empty();
            }

            @Override
            public Optional<PublicKey> getCached(MysterAddress ignored) {
                return Optional.of(conflictingCachedKey.getPublic());
            }

            @Override
            public com.general.thread.PromiseFuture<Optional<PublicKey>> fetchPublicKey(
                    MysterAddress ignored) {
                return com.general.thread.PromiseFuture.newPromiseFuture(Optional.empty());
            }
        };
        MysterDatagramImpl datagram = new MysterDatagramImpl(transactionManager,
                                                             mock(UDPPingClient.class),
                                                             lookup,
                                                             () -> "test",
                                                             () -> 6669,
                                                             null,
                                                             mock(FileTypeListManager.class));

        ThreeDnsAddressCandidate candidate = new ThreeDnsAddressCandidate(
                new PublicKeyIdentity(expectedKey.getPublic()), address);
        ServerCid target = new ServerCid(new byte[ServerCid.LENGTH]);
        var verifiedFuture = new ThreeDnsPeerClient(datagram).findClosest(candidate, target, 2);

        assertEquals(DatagramConstants.STLS_CODE, sentCode.get());
        assertThrows(DatagramEncryptUtil.DecryptionException.class,
                     () -> DatagramEncryptUtil.decryptRequestPacket(
                             sentPacket.get().getData(),
                             keyLookup(conflictingCachedKey)));
        DatagramEncryptUtil.R decrypted = DatagramEncryptUtil.decryptRequestPacket(
                sentPacket.get().getData(),
                keyLookup(expectedKey));
        ByteBuffer payload = ByteBuffer.wrap(decrypted.payload);
        assertEquals(DatagramConstants.THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE, payload.getInt());
        byte[] innerPayload = new byte[payload.remaining()];
        payload.get(innerPayload);
        assertTrue(innerPayload.length > ServerCid.LENGTH);

        MessagePak response = MessagePak.newEmpty();
        response.putInt("/schemaVersion", 1);
        response.putInt("/exactCount", 0);
        response.putInt("/leftCount", 0);
        response.putInt("/rightCount", 0);
        byte[] encryptedResponse = DatagramEncryptUtil.encryptResponsePacket(
                response.toBytes(), decrypted.syncDecryptKey, Optional.of(expectedKey));
        Transaction reply = mock(Transaction.class);
        when(reply.isError()).thenReturn(false);
        when(reply.getData()).thenReturn(encryptedResponse);
        when(reply.withDifferentPayload(any(byte[].class), anyInt())).thenAnswer(invocation -> {
            Transaction decryptedReply = mock(Transaction.class);
            when(decryptedReply.getData()).thenReturn(invocation.getArgument(0));
            return decryptedReply;
        });
        TransactionEvent event = mock(TransactionEvent.class);
        when(event.getTransaction()).thenReturn(reply);

        sentListener.get().transactionReply(event);
        ThreeDnsVerifiedQueryResult verified = verifiedFuture.get();
        assertEquals(candidate.identity(), verified.responder().identity());
        assertEquals(candidate.cid(), verified.responder().cid());
        assertEquals(candidate.address(), verified.responder().address());
        assertTrue(verified.candidates().exact().isEmpty());
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
