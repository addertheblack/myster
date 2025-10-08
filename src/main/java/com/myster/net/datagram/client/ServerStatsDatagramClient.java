package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.myster.mml.MMLException;
import com.myster.mml.MessagePack;
import com.myster.mml.RobustMML;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.transaction.Transaction;

public class ServerStatsDatagramClient implements StandardDatagramClientImpl<MessagePack> {
    public static final int SERVER_STATS_TRANSACTION_CODE = 101;

    // returns RobustMML
    public MessagePack getObjectFromTransaction(Transaction transaction)
            throws IOException {
        // gets the byte[] puts it into a ByteArrayInputStream and puts THAT
        // into a
        // DataInputStream then gets a UTF string and puts that into a RobustMML
        // constructor..
        // yeah baby... :-)
        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            return in.readMessagePack();
        }
    }

    public byte[] getDataForOutgoingPacket() {
        return new byte[0];
    }

    public int getCode() {
        return SERVER_STATS_TRANSACTION_CODE;
    }
}