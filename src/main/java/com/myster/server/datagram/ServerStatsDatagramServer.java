package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class ServerStatsDatagramServer implements TransactionProtocol {
    public static final int SERVER_STATS_TRANSACTION_CODE = com.myster.client.datagram.ServerStatsDatagramClient.SERVER_STATS_TRANSACTION_CODE;
    private final Supplier<String> getIdentity;

    public ServerStatsDatagramServer(Supplier<String> getIdentity) {
        this.getIdentity = getIdentity;
    }

    @Override
    public int getTransactionCode() {
        return SERVER_STATS_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender, Transaction transaction, Object transactionObject) throws BadPacketException {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            
            // TODO: pass in getMMLToSend as the supplier
            out.writeUTF("" + com.myster.server.stream.ServerStats.getMMLToSend(getIdentity.get()));

            sender.sendTransaction(new Transaction(transaction, byteOutputStream.toByteArray(),
                    Transaction.NO_ERROR));

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}