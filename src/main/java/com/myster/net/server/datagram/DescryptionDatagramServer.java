package com.myster.net.server.datagram;

import com.general.net.ImmutableDatagramPacket;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.datagram.DatagramSender;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class DescryptionDatagramServer implements TransactionProtocol {
    public interface TransactionManager {
        void packetReceived(DatagramSender sender, ImmutableDatagramPacket immutablePacket);
    }
    
    private final TransactionManager manager;
    
    public DescryptionDatagramServer(TransactionManager manager) {
        this.manager = manager;
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
        // Decrypt packet
        
        // If decryption went ok then pass the decrypted payload with suitable headers to the manager
        manager.packetReceived(null, null); // fill in
        
        // If decryption went badly then send error packet back to the sender with a decryption error code
    }
}