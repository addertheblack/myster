package com.myster.net.datagram.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.mml.MessagePack;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.search.MysterFileStub;
import com.myster.transaction.Transaction;

public class FileStatsDatagramClient implements StandardDatagramClientImpl<MessagePack> {
    public static final int FILE_STATS_TRANSACTION_CODE = 77;

    private MysterFileStub stub;

    public FileStatsDatagramClient(MysterFileStub stub) {
        this.stub = stub;
    }

    // returns MessagePack
    public MessagePack getObjectFromTransaction(Transaction transaction)
            throws IOException {
        // Parse the MessagePack bytes from the transaction using the robust variant
        try {
            return MessagePack.fromBytes(transaction.getData());
        } catch (IOException ex) {
            throw new com.myster.net.datagram.BadPacketException(
                    "Received a badly formed MessagePack from the server : "
                            + transaction.getAddress() + " & " + ex);
        }
    }

    public byte[] getDataForOutgoingPacket() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (final var out = new MysterDataOutputStream(byteArrayOutputStream)) {
            out.writeType(stub.getType());
            out.writeUTF(stub.getName());
        } catch (IOException ex) {
            throw new com.general.util.UnexpectedException(ex);
        }
        
        return byteArrayOutputStream.toByteArray();
    }

    public int getCode() {
        return FILE_STATS_TRANSACTION_CODE;
    }
}