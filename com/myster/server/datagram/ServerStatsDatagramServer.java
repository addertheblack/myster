package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;

public class ServerStatsDatagramServer extends TransactionProtocol {
    public static final int SERVER_STATS_TRANSACTION_CODE = com.myster.client.datagram.ServerStatsDatagramClient.SERVER_STATS_TRANSACTION_CODE;

    public int getTransactionCode() {
        return SERVER_STATS_TRANSACTION_CODE;
    }

    public void transactionReceived(Transaction transaction, Object transactionObject) throws BadPacketException {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            out.writeUTF("" + com.myster.server.stream.HandshakeThread.getMMLToSend());

            sendTransaction(new Transaction(transaction, byteOutputStream.toByteArray(),
                    Transaction.NO_ERROR));

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}