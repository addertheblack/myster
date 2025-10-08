package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.net.datagram.BadPacketException;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.tracker.MysterServer;
import com.myster.tracker.Tracker;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

public class TopTenDatagramServer implements TransactionProtocol {
    public static final int NUMBER_OF_SERVERS_TO_RETURN = 100;

    public static final int TOP_TEN_TRANSACTION_CODE = com.myster.net.datagram.client.TopTenDatagramClient.TOP_TEN_TRANSACTION_CODE;

    private final Tracker tracker;

    public TopTenDatagramServer(Tracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public int getTransactionCode() {
        return TOP_TEN_TRANSACTION_CODE;
    }

    
    // unittest
    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {
            tracker.addIp(transaction.getAddress());

            MysterServer[] topTenServers = tracker.getTop(
                    getTypeFromTransaction(transaction), NUMBER_OF_SERVERS_TO_RETURN);

            String[] topTenStrings = (topTenServers == null ? new String[0]
                    : new String[countServersReturned(topTenServers)]);

            for (int i = 0; i < topTenStrings.length; i++) {
                final var hack = i;
                topTenStrings[i] = topTenServers[i].getBestAddress()
                        .orElseGet(() -> topTenServers[hack].getAddresses()[0]).toString();
            }

            sender.sendTransaction(new Transaction(transaction, getBytesFromStrings(topTenStrings),
                    Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }

    private static int countServersReturned(MysterServer[] servers) {
        for (int i = 0; i < servers.length; i++) {
            if (servers[i] == null) {
                return i;
            }
        }

        return servers.length;
    }

    public static byte[] getBytesFromStrings(String[] addressesAsStrings) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(byteOutputStream)) {
            for (int i = 0; i < addressesAsStrings.length; i++) {
                out.writeUTF(addressesAsStrings[i]);
            }

            out.writeUTF("");
        }

        return byteOutputStream.toByteArray();
    }

    @SuppressWarnings("resource")
    private static MysterType getTypeFromTransaction(Transaction transaction) throws IOException {
        byte[] bytes = transaction.getData();

        return new MysterDataInputStream(new ByteArrayInputStream(bytes)).readType();
    }
}