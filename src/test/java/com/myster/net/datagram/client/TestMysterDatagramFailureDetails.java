package com.myster.net.datagram.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.net.datagram.DatagramEncryptUtil.DecryptionException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionManager;

class TestMysterDatagramFailureDetails {
    @Test
    void failedAuthenticationRetainsDecryptionSubtypeAndCryptoCause() throws Exception {
        AtomicReference<TransactionListener> listener = new AtomicReference<>();
        MysterDatagramImpl datagram = datagram(listener);
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var future = datagram.findClosest(new ParamBuilder(MysterAddress.createMysterAddress("127.0.0.1"))
                .withExpectedServerPublicKey(key.getPublic()), new ServerCid(new byte[16]), 2);
        // A structurally valid response encrypted with a different key must fail authentication.
        byte[] response = DatagramEncryptUtil.encryptResponsePacket(new byte[0], new byte[32], Optional.empty());

        listener.get().transactionReply(reply(response));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        DecryptionException decryptFailure = assertInstanceOf(DecryptionException.class, failure.getCause());
        assertInstanceOf(org.bouncycastle.crypto.InvalidCipherTextException.class, decryptFailure.getCause());
    }

    @Test
    void malformedFindClosestResponseRetainsDecoderCause() throws Exception {
        AtomicReference<TransactionListener> listener = new AtomicReference<>();
        MysterDatagramImpl datagram = datagram(listener);
        var future = datagram.findClosest(new ParamBuilder(MysterAddress.createMysterAddress("127.0.0.1")),
                new ServerCid(new byte[16]), 2);
        MessagePak response = MessagePak.newEmpty();
        response.putInt("/schemaVersion", 99);

        listener.get().transactionReply(reply(response.toBytes()));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        BadPacketException packetFailure = assertInstanceOf(BadPacketException.class, failure.getCause());
        assertEquals("Unsupported 3DNS schema version: 99", packetFailure.getCause().getMessage());
        assertEquals(FindClosestDatagramClient.class.getName(), packetFailure.getCause().getStackTrace()[0].getClassName());
    }

    private static MysterDatagramImpl datagram(AtomicReference<TransactionListener> listener) {
        TransactionManager transactions = mock(TransactionManager.class);
        when(transactions.sendTransaction(any(), anyInt(), any(TransactionListener.class))).thenAnswer(call -> {
            listener.set(call.getArgument(2));
            return 1;
        });
        return new MysterDatagramImpl(transactions, mock(UDPPingClient.class), mock(PublicKeyLookup.class),
                () -> "test", () -> "", () -> 6669, null, mock(FileTypeListManager.class));
    }

    private static TransactionEvent reply(byte[] payload) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getData()).thenReturn(payload);
        TransactionEvent event = mock(TransactionEvent.class);
        when(event.getTransaction()).thenReturn(transaction);
        return event;
    }
}
