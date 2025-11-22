package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.myster.mml.MessagePak;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.transaction.Transaction;

public class ServerStatsDatagramClient implements StandardDatagramClientImpl<MessagePak> {
    // returns MessagePack
    public MessagePak getObjectFromTransaction(Transaction transaction)
            throws IOException {
        // gets the byte[] puts it into a ByteArrayInputStream and puts THAT
        // into a DataInputStream then reads MessagePack data directly
        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            return in.readMessagePack();
        }
    }

    public byte[] getDataForOutgoingPacket() {
        return new byte[0];
    }

    public int getCode() {
        return DatagramConstants.SERVER_STATS_TRANSACTION_CODE;
    }
}