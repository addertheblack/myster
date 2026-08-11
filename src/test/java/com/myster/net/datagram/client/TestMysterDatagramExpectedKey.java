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
import com.myster.identity.Cid128;
import com.myster.net.MysterAddress;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.DataPacket;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
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
        TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.sendTransaction(any(), anyInt(), any(TransactionListener.class)))
                .thenAnswer(invocation -> {
                    sentPacket.set(invocation.getArgument(0));
                    sentCode.set(invocation.getArgument(1));
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

        datagram.findClosest(params, new Cid128(new byte[Cid128.LENGTH]), 2);

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
        assertTrue(innerPayload.length > Cid128.LENGTH);
    }

    private static DatagramEncryptUtil.Lookup keyLookup(KeyPair keyPair) {
        return new DatagramEncryptUtil.Lookup() {
            @Override
            public Optional<PublicKey> findPublicKey(byte[] keyHash) {
                return Optional.empty();
            }

            @Override
            public Optional<KeyPair> getServerKeyPair(Object serverId) {
                return Optional.of(keyPair);
            }
        };
    }
}
