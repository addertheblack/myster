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
    public byte[] getDataForOutgoingPacket() {
        try {
            // Get original payload and transaction code from the delegate
            byte[] originalPayload = delegate.getDataForOutgoingPacket();
            int originalTransactionCode = delegate.getCode();
            
            // Build encrypted payload: [original_transaction_code (4 bytes) | original_payload]
            // This format allows the server to extract the original transaction code after decryption
            ByteBuffer encryptedPayload = ByteBuffer.allocate(4 + originalPayload.length);
            encryptedPayload.putInt(originalTransactionCode);  // First 32 bits = original transaction code
            encryptedPayload.put(originalPayload);             // Rest = original payload
            
            // Encrypt using MSD protocol - unsigned if no client identity
            lastEncryptedRequest = DatagramEncryptUtil.encryptPacket(
                encryptedPayload.array(), 
                serverPublicKey, 
                clientIdentity
            );
            
            return lastEncryptedRequest.encryptedPacket;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt packet", e);
        }
    }
    
    @Override
    public int getCode() {
        // Always return STLS code for encrypted packets - the original code is embedded in the payload
        return DatagramConstants.STLS_CODE;
    }
    
    @Override
    public T getObjectFromTransaction(Transaction encryptedReply) throws IOException {
        try {
            // Decrypt the response using our stored symmetric key from the request
            byte[] encryptedResponseData = encryptedReply.getData();
            byte[] decryptedResponse = DatagramEncryptUtil.decryptResponsePacket(
                encryptedResponseData, 
                lastEncryptedRequest.symmetricKey
            );
            
            // Create new transaction with decrypted data and original transaction code
            Transaction decryptedTransaction = encryptedReply.withDifferentPayload(
                decryptedResponse, 
                delegate.getCode()
            );
            
            // Forward to original implementation with the decrypted transaction
            return delegate.getObjectFromTransaction(decryptedTransaction);
            
        } catch (Exception e) {
            throw new IOException("Failed to decrypt response", e);
        }
    }
}