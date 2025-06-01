package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MML;
import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

public class FileStatsDatagramServer implements TransactionProtocol {
    public static final int FILE_STATS_TRANSACTION_CODE =
            com.myster.client.datagram.FileStatsDatagramClient.FILE_STATS_TRANSACTION_CODE;
    
    private final FileTypeListManager fileManager;

    public FileStatsDatagramServer(FileTypeListManager fileManager) {
        this.fileManager = fileManager;
    }
    
    public int getTransactionCode() {
        return FILE_STATS_TRANSACTION_CODE;
    }

    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {
            MysterDataInputStream in =
                    new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()));

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            MysterDataOutputStream out = new MysterDataOutputStream(byteOutputStream);

            MysterType type = in.readType();
            String filename = in.readUTF();

            FileItem fileItem = fileManager.getFileItem(type, filename);
            MML mml;

            if (fileItem == null) { //file not found
                mml = new MML();
            } else {
                mml = fileItem.getMMLRepresentation();
            }

            out.writeUTF(mml.toString());
            
            // closing not really needed for byte streams but it makes the compiler happy
            out.close();
            in.close();

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}