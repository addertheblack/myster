package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class SearchDatagramClient implements StandardDatagramClientImpl {
    public static final int SEARCH_TRANSACTION_CODE = 35; //There is no UDP
                                                          // version of the
                                                          // first version of
                                                          // get file type list.

    MysterType type;

    String searchString;

    public SearchDatagramClient(MysterType type, String searchString) {
        this.type = type;
        this.searchString = searchString;
    }

    //NOTE: The UDP version of this section (below) is different than the older
    // TCP veison and WILL cause problems
    //		if the txt encoding of the type in question is outside the first 7 bits
    // (ascii) and the
    //		text encoding is different..

    //returns Vector of Strings
    public Object getObjectFromTransaction(Transaction transaction)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                transaction.getData()));

        Vector searchResults = new Vector(100, 100);

        for (;;) {
            String searchResult = in.readUTF();

            if (searchResult.equals(""))
                break;

            searchResults.addElement(searchResult);
        }

        return searchResults;
    }

    //returns Vector of Strings
    public Object getNullObject() {
        return new Vector();
    }

    public byte[] getDataForOutgoingPacket() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {

            DataOutputStream out = new DataOutputStream(byteArrayOutputStream);

            out.writeInt(type.getAsInt());
            out.writeUTF(searchString);
        } catch (IOException ex) {
            throw new com.general.util.UnexpectedException(ex);
        }

        return byteArrayOutputStream.toByteArray();
    }

    public int getCode() {
        return SEARCH_TRANSACTION_CODE;
    }
}