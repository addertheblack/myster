package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.net.BadPacketException;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.type.MysterType;

public class SearchDatagramServer extends TransactionProtocol {
    public static final int SEARCH_TRANSACTION_CODE = com.myster.client.datagram.SearchDatagramClient.SEARCH_TRANSACTION_CODE;

    public int getTransactionCode() {
        return SEARCH_TRANSACTION_CODE;
    }

    public Object getTransactionObject() {
        return new ServerSearchDispatcher();
    }

    public void transactionReceived(Transaction transaction, Object transactionObject)
            throws BadPacketException {
        try {
            DataInputStream in = new DataInputStream(
                    new ByteArrayInputStream(transaction.getData()));

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            String searchstring;
            String tempstring;

            MysterType type = new MysterType(in.readInt());
            searchstring = in.readUTF();

            ServerSearchDispatcher dispatcher = (ServerSearchDispatcher) transactionObject;

            dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.REQUESTED, transaction
                    .getAddress(), getTransactionCode(), searchstring, type));

            String[] stringarray;

            stringarray = com.myster.filemanager.FileTypeListManager.getInstance().getDirList(type,
                    searchstring); //does the
            // search
            // matching

            if (stringarray != null) {
                dispatcher.fireEvent(new ServerSearchEvent(ServerSearchEvent.RESULTS, transaction
                        .getAddress(), getTransactionCode(), searchstring, type, stringarray));
                for (int j = 0; j < stringarray.length; j++) {
                    out.writeUTF(stringarray[j]);
                    if (byteOutputStream.size() > 3500) // 4000 is about max size of a packet..
                        break; // ok stop now.
                }
            }

            out.writeUTF("");

            sendTransaction(new Transaction(transaction, byteOutputStream.toByteArray(),
                    Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}