package com.myster.net.server.datagram;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramEncryptUtil;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class EncryptedDatagramServer implements TransactionProtocol {
    private static final Logger LOGGER = Logger.getLogger(EncryptedDatagramServer.class.getName());

    public interface TransactionManager {
        void resendTransaction(TransactionSender sender, Transaction transaction)
                throws BadPacketException;
    }

    private final TransactionManager manager;
    private final DatagramEncryptUtil.Lookup keyLookup;

    public EncryptedDatagramServer(TransactionManager manager,
                                   DatagramEncryptUtil.Lookup keyLookup) {
        this.manager = manager;
        this.keyLookup = keyLookup;
    }

    @Override
    public int getTransactionCode() {
        return DatagramConstants.STLS_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {
            LOGGER.fine("Server got an encrypted packet");
            
            // Decrypt the MSD packet using the protocol utilities
            DatagramEncryptUtil.R decryptResult = DatagramEncryptUtil.decryptRequestPacket(
                transaction.getData(), 
                keyLookup
            );
            
            // Extract original transaction code from first 4 bytes of decrypted payload
            byte[] decryptedPayload = decryptResult.payload;
            if (decryptedPayload.length < 4) {
                LOGGER.warning("Decrypted payload too short from " + transaction.getAddress());
                sendDecryptionError(sender, transaction);
                return;
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(decryptedPayload);
            int originalTransactionCode = buffer.getInt();  // First 32 bits
            byte[] originalPayload = new byte[buffer.remaining()];
            buffer.get(originalPayload);                    // Rest of the payload
            
            // Create new transaction with decrypted data and original transaction code
            Transaction decryptedTransaction = transaction.withDifferentPayload(
                originalPayload, 
                originalTransactionCode
            );
            
            var encrypterSender = new TransactionSender() {
                @Override
                public void sendTransaction(Transaction t) {
                    byte[] encryptedData = DatagramEncryptUtil
                            .encryptResponsePacket(t.getData(),
                                                   decryptResult.syncDecryptKey,
                                                   keyLookup.getServerKeyPair(null));
                    Transaction encryptedTransaction =
                            t.withDifferentPayload(encryptedData, DatagramConstants.STLS_CODE);
                    
                    LOGGER.fine("Server sent an encrypted reply packet");
                    sender.sendTransaction(encryptedTransaction);
                }
            };
            
            // Forward to transaction manager as if it received the original unencrypted packet
            manager.resendTransaction(encrypterSender, decryptedTransaction);
            
            LOGGER.fine("Successfully decrypted and forwarded transaction code " + originalTransactionCode 
                       + " from " + transaction.getAddress());
            
        } catch (DatagramEncryptUtil.DecryptionException e) {
            LOGGER.warning("Failed to decrypt packet from " + transaction.getAddress() + ": " + e.getMessage());
            sendDecryptionError(sender, transaction);
            throw new BadPacketException("Packet decryption failed: " + e.getMessage());
        }
    }
    
    private void sendDecryptionError(TransactionSender sender, Transaction originalTransaction) {
        try {
            // Create error response using the available public constructor
            Transaction errorResponse = new Transaction(
                originalTransaction,
                new byte[0], 
                DatagramConstants.DECRYPTION_ERROR
            );
            
            sender.sendTransaction(errorResponse);
        } catch (RuntimeException e) {
            LOGGER.severe("Failed to send decryption error response: " + e.getMessage());
            // Don't re-throw - we've already logged the issue
        }
    }
}