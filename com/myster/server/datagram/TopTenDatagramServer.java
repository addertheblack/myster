package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.net.BadPacketException;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.tracker.MysterServer;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.type.MysterType;

public class TopTenDatagramServer extends TransactionProtocol {
    public static final int NUMBER_OF_SERVERS_TO_RETURN = 100;

    public static final int TOP_TEN_TRANSACTION_CODE = com.myster.client.datagram.TopTenDatagramClient.TOP_TEN_TRANSACTION_CODE;

    public int getTransactionCode() {
        return TOP_TEN_TRANSACTION_CODE;
    }

    public void transactionReceived(Transaction transaction, Object transactionObject) throws BadPacketException {
        try {
            IPListManagerSingleton.getIPListManager().addIP(transaction.getAddress());

            MysterServer[] topTenServers = IPListManagerSingleton.getIPListManager().getTop(
                    getTypeFromTransaction(transaction), NUMBER_OF_SERVERS_TO_RETURN);

            String[] topTenStrings = (topTenServers == null ? new String[0]
                    : new String[countServersReturned(topTenServers)]);

            for (int i = 0; i < topTenStrings.length; i++) {
                topTenStrings[i] = topTenServers[i].getAddress().toString();
            }

            sendTransaction(new Transaction(transaction, getBytesFromStrings(topTenStrings),
                    Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }

    private int countServersReturned(MysterServer[] servers) {
        for (int i = 0; i < servers.length; i++) {
            if (servers[i] == null) {
                return i;
            }
        }

        return servers.length;
    }

    public byte[] getBytesFromStrings(String[] addressesAsStrings) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOutputStream);

        for (int i = 0; i < addressesAsStrings.length; i++) {
            out.writeUTF(addressesAsStrings[i]);
        }

        out.writeUTF("");

        return byteOutputStream.toByteArray();
    }

    private MysterType getTypeFromTransaction(Transaction transaction) throws IOException {
        byte[] bytes = transaction.getData();

        if (bytes.length != 4)
            throw new IOException("Packet is the wrong length");

        return new MysterType((new DataInputStream(new ByteArrayInputStream(bytes))).readInt());
    }
}