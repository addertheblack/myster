package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

/**
 * Server side datagram implementation of Myster type lister conneciton section. 
 */
public class TypeDatagramServer implements TransactionProtocol {
    public static final int NUMBER_OF_FILE_TYPE_TO_RETURN = 100;
    public static final int TYPE_TRANSACTION_CODE = com.myster.net.datagram.client.TypeDatagramClient.TYPE_TRANSACTION_CODE;

    private final FileTypeListManager fileManager;

    public TypeDatagramServer(FileTypeListManager fileManager) {
        this.fileManager = fileManager;
    }
    
    @Override
    public int getTransactionCode() {
        return TYPE_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        
        MysterType[] temp;
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (MysterDataOutputStream out = new MysterDataOutputStream(byteOutputStream)) {
                temp = fileManager.getFileTypeListing();

                out.writeInt(temp.length);

                for (int i = 0; i < temp.length; i++) {
                    out.writeType(temp[i]); //BAD protocol
                }

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}