package com.myster.net.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MessagePak;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

public class FileStatsDatagramServer implements TransactionProtocol {
    private final FileTypeListManager fileManager;
    private final AccessListReader accessListReader;

    public FileStatsDatagramServer(FileTypeListManager fileManager, AccessListReader accessListReader) {
        this.fileManager = fileManager;
        this.accessListReader = accessListReader;
    }
    
    public int getTransactionCode() {
        return DatagramConstants.FILE_STATS_TRANSACTION_CODE;
    }

    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {
            MysterDataInputStream in =
                    new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()));

            MysterType type = in.readType();
            String filename = in.readUTF();

            FileItem fileItem = AccessEnforcementUtils.isAllowed(
                    type, transaction.callerCid(), accessListReader)
                            ? fileManager.getFileItem(type, filename)
                            : null;
            MessagePak messagePack;

            if (fileItem == null) { //file not found
                messagePack = MessagePak.newEmpty();
            } else {
                messagePack = fileItem.getMessagePackRepresentation();
            }

            byte[] messagePackBytes = messagePack.toBytes();
            
            // closing not really needed for byte streams but it makes the compiler happy
            in.close();

            sender.sendTransaction(new Transaction(transaction,
                                                   messagePackBytes,
                                                   DatagramConstants.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}