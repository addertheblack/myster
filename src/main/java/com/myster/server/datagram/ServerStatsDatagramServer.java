package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

import com.myster.identity.Identity;
import com.myster.net.BadPacketException;
import com.myster.server.stream.NotInitializedException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class ServerStatsDatagramServer implements TransactionProtocol {
    public static final int SERVER_STATS_TRANSACTION_CODE = com.myster.client.datagram.ServerStatsDatagramClient.SERVER_STATS_TRANSACTION_CODE;
    private final Supplier<String> getIdentity;
    private final Identity identity;
    private Supplier<Integer> getPort;

    public ServerStatsDatagramServer(Supplier<String> getIdentity, Supplier<Integer> getPort, Identity identity) {
        this.getPort = getPort;
        this.getIdentity = getIdentity;
        this.identity = identity;
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
            out.writeUTF("" + com.myster.server.stream.ServerStats.getMMLToSend(getIdentity.get(), getPort.get(), identity));

            sender.sendTransaction(new Transaction(transaction, byteOutputStream.toByteArray(),
                    Transaction.NO_ERROR));

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        } catch (NotInitializedException exception) {
            // nothing..
            System.out.println("Could not reply server stats, file manager is not inited yet");
        }
    }
}