package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.search.MysterFileStub;
import com.myster.transaction.Transaction;

public class FileStatsDatagramClient implements StandardDatagramClientImpl {
    public static final int FILE_STATS_TRANSACTION_CODE = 77;

    private MysterFileStub stub;

    public FileStatsDatagramClient(MysterFileStub stub) {
        this.stub = stub;
    }

    // returns RobustMML
    public Object getObjectFromTransaction(Transaction transaction)
            throws IOException {
        //gets the byte[] puts it into a ByteArrayInputStream and puts THAT
        // into a
        //DataInputStream then gets a UTF string and puts that into a RobustMML
        // constructor..
        //yeah baby... :-)
        try {
            return new RobustMML((new DataInputStream(new ByteArrayInputStream(
                    transaction.getData()))).readUTF());
        } catch (MMLException ex) {
            throw new com.myster.net.BadPacketException(
                    "Recieved a badly formed MML string from the server : "
                            + transaction.getAddress() + " & " + ex);
        }
    }

    // returns RobustMML
    public Object getNullObject() {
        return new RobustMML();
    }

    public byte[] getDataForOutgoingPacket() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            DataOutputStream out = new DataOutputStream(byteArrayOutputStream);

            out.writeInt(stub.getType().getAsInt()); //this protocol sucks
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