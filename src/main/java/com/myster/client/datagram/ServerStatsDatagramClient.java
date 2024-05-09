package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import com.myster.client.stream.MysterDataInputStream;
import java.io.IOException;

import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;

public class ServerStatsDatagramClient implements StandardDatagramClientImpl<RobustMML> {
    public static final int SERVER_STATS_TRANSACTION_CODE = 101;

    // returns RobustMML
    @SuppressWarnings("resource")
    public RobustMML getObjectFromTransaction(Transaction transaction)
            throws IOException {
        // gets the byte[] puts it into a ByteArrayInputStream and puts THAT
        // into a
        // DataInputStream then gets a UTF string and puts that into a RobustMML
        // constructor..
        // yeah baby... :-)
        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            return new RobustMML(in.readUTF());
        } catch (MMLException ex) {
            throw new com.myster.net.BadPacketException("Recieved a badly formed MML string from the server : "
                    + transaction.getAddress() + " & " + ex);
        }
    }

    // returns RobustMML
    public Object getNullObject() {
        return new RobustMML();
    }

    public byte[] getDataForOutgoingPacket() {
        return new byte[0];
    }

    public int getCode() {
        return SERVER_STATS_TRANSACTION_CODE;
    }
}