package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionManager;
import com.myster.transaction.TransactionProtocol;
import com.myster.type.MysterType;

public class TypeDatagramServer extends TransactionProtocol {
    public static final int NUMBER_OF_FILE_TYPE_TO_RETURN = 100;

    public static final int TYPE_TRANSACTION_CODE = com.myster.client.datagram.TypeDatagramClient.TYPE_TRANSACTION_CODE;

    static boolean alreadyInit = false;

    public synchronized static void init() {
        if (alreadyInit)
            return; //should not be init twice

        TransactionManager.addTransactionProtocol(new TypeDatagramServer());
    }

    public int getTransactionCode() {
        return TYPE_TRANSACTION_CODE;
    }

    public void transactionReceived(Transaction transaction)
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

            sendTransaction(new Transaction(transaction, byteOutputStream
                    .toByteArray(), Transaction.NO_ERROR));

            System.out.println("SIZE OF ARRAY IS -> "
                    + byteOutputStream.toByteArray().length);

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}