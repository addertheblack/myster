package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.server.event.ServerSearchDispatcher;
import com.myster.server.event.ServerSearchEvent;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

public class SearchDatagramServer implements TransactionProtocol {
    public static final int SEARCH_TRANSACTION_CODE = com.myster.net.datagram.client.SearchDatagramClient.SEARCH_TRANSACTION_CODE;
    
    private final FileTypeListManager fileManager;

    public SearchDatagramServer(FileTypeListManager fileManager) {
        this.fileManager = fileManager;
    }
    
    @Override
    public int getTransactionCode() {
        return SEARCH_TRANSACTION_CODE;
    }

    @Override
    public Object getTransactionObject() {
        return new ServerSearchDispatcher();
    }

    @Override
    public void transactionReceived(TransactionSender sender, Transaction transaction, Object transactionObject)
            throws BadPacketException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (MysterDataInputStream in = new MysterDataInputStream(
                new ByteArrayInputStream(transaction.getData()));
                MysterDataOutputStream out = new MysterDataOutputStream(byteOutputStream)) {
            String searchstring;

            MysterType type = in.readType();
            searchstring = in.readUTF();

            ServerSearchDispatcher dispatcher = (ServerSearchDispatcher) transactionObject;

            dispatcher.fire().searchRequested(new ServerSearchEvent(transaction
                    .getAddress(), getTransactionCode(), searchstring, type));

            String[] stringarray;

            stringarray = fileManager.getDirList(type,
                    searchstring); //does the
            // search
            // matching

            if (stringarray != null) {
                dispatcher.fire().searchResult(new ServerSearchEvent(transaction
                        .getAddress(), getTransactionCode(), searchstring, type, stringarray));
                for (int j = 0; j < stringarray.length; j++) {
                    out.writeUTF(stringarray[j]);
                    if (byteOutputStream.size() > 3500) // 4000 is about max size of a packet..
                        break; // ok stop now.
                }
            }

            out.writeUTF("");

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}