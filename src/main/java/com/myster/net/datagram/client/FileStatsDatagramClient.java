package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.search.MysterFileStub;
import com.myster.transaction.Transaction;

public class FileStatsDatagramClient implements StandardDatagramClientImpl<RobustMML> {
    public static final int FILE_STATS_TRANSACTION_CODE = 77;

    private MysterFileStub stub;

    public FileStatsDatagramClient(MysterFileStub stub) {
        this.stub = stub;
    }

    // returns RobustMML
    public RobustMML getObjectFromTransaction(Transaction transaction)
            throws IOException {
        //gets the byte[] puts it into a ByteArrayInputStream and puts THAT
        // into a
        //DataInputStream then gets a UTF string and puts that into a RobustMML
        // constructor..
        //yeah baby... :-)
        try {
            return new RobustMML((new MysterDataInputStream(new ByteArrayInputStream(
                    transaction.getData()))).readUTF());
        } catch (MMLException ex) {
            throw new com.myster.net.datagram.BadPacketException(
                    "Recieved a badly formed MML string from the server : "
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