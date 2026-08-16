package com.myster.net.datagram.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.Optional;

import com.myster.identity.Identity;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.transaction.Transaction;

/**
 * Decorator that wraps any StandardDatagramClientImpl with MSD encryption/decryption.
 * Handles the crypto transparently while preserving all original transaction logic.
 * 
 * The encrypted payload format is: [original_transaction_code (4 bytes) | original_payload]
 * This allows the server's STLS handler to extract the original transaction code and forward
 * the original payload to the appropriate transaction handler.
 */
public class EncryptingStandardDatagramClientImpl<T> implements StandardDatagramClientImpl<T> {
    private final StandardDatagramClientImpl<T> delegate;
    private final PublicKey serverPublicKey;
    private final Optional<Identity> clientIdentity;
    private volatile DatagramEncryptUtil.EncryptedRequest lastEncryptedRequest;
    
    public EncryptingStandardDatagramClientImpl(StandardDatagramClientImpl<T> delegate,
                                               PublicKey serverPublicKey,
                                               Optional<Identity> clientIdentity) {
        this.delegate = delegate;
        this.serverPublicKey = serverPublicKey;
        this.clientIdentity = clientIdentity;
    }
    
    @Override
    public byte[] getDataForOutgoingPacket() throws IOException {
        byte[] originalPayload = delegate.getDataForOutgoingPacket();
        int originalTransactionCode = delegate.getCode();

        ByteBuffer encryptedPayload = ByteBuffer.allocate(4 + originalPayload.length);
        encryptedPayload.putInt(originalTransactionCode);
        encryptedPayload.put(originalPayload);

        lastEncryptedRequest = DatagramEncryptUtil.encryptPacket(
            encryptedPayload.array(),
            serverPublicKey,
            clientIdentity
        );

        return lastEncryptedRequest.encryptedPacket;
    }
    
    @Override
    public int getCode() {
        // Always return STLS code for encrypted packets - the original code is embedded in the payload
        return DatagramConstants.STLS_CODE;
    }
    
    @Override
    public T getObjectFromTransaction(Transaction encryptedReply) throws IOException {
        byte[] decryptedResponse = DatagramEncryptUtil.decryptResponsePacket(
            encryptedReply.getData(),
            lastEncryptedRequest.symmetricKey
        );

        Transaction decryptedTransaction = encryptedReply.withDifferentPayload(
            decryptedResponse,
            delegate.getCode()
        );

        return delegate.getObjectFromTransaction(decryptedTransaction);
    }
}
