package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MML;
import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.type.MysterType;

public class FileStatsDatagramServer extends TransactionProtocol {
    public static final int FILE_STATS_TRANSACTION_CODE = com.myster.client.datagram.FileStatsDatagramClient.FILE_STATS_TRANSACTION_CODE;

    static volatile boolean alreadyInit = false;

    public static void init() {
        if (alreadyInit)
            return; //should not be init twice

        TransactionManager
                .addTransactionProtocol(new FileStatsDatagramServer());
    }

    public int getTransactionCode() {
        return FILE_STATS_TRANSACTION_CODE;
    }

    public void transactionReceived(Transaction transaction)
            throws BadPacketException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                    transaction.getData()));

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            MysterType type = new MysterType(in.readInt());
            String filename = in.readUTF();

            FileItem fileItem = FileTypeListManager.getInstance().getFileItem(
                    type, filename);
            MML mml;

            if (fileItem == null) { //file not found
                mml = new MML();
            } else {
                mml = fileItem.getMMLRepresentation();
            }

            if (type.equals("MPG3"))
                com.myster.server.stream.FileInfoLister.patchFunction2(mml,
                        FileTypeListManager.getInstance().getFile(type,
                                filename));

            out.writeUTF(mml.toString());

            sendTransaction(new Transaction(transaction, byteOutputStream
                    .toByteArray(), Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}