package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

/**
 * Server side datagram implementation of Myster type lister conneciton section. 
 */
public class TypeDatagramServer implements TransactionProtocol {
    public static final int NUMBER_OF_FILE_TYPE_TO_RETURN = 100;

    public static final int TYPE_TRANSACTION_CODE = com.myster.client.datagram.TypeDatagramClient.TYPE_TRANSACTION_CODE;

    @Override
    public int getTransactionCode() {
        return TYPE_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        try {

            MysterType[] temp;

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            temp = FileTypeListManager.getInstance().getFileTypeListing();

            out.writeInt(temp.length);

            for (int i = 0; i < temp.length; i++) {
                out.writeInt(temp[i].getAsInt()); //BAD protocol
            }

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));

            System.out.println("SIZE OF ARRAY IS -> "
                    + byteOutputStream.toByteArray().length);

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}