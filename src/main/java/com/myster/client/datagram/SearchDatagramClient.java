package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class SearchDatagramClient implements StandardDatagramClientImpl<List<String>> {
    public static final int SEARCH_TRANSACTION_CODE = 35; //There is no UDP
                                                          // version of the
                                                          // first version of
                                                          // get file type list.

    private final MysterType type;
    private final String searchString;

    public SearchDatagramClient(MysterType type, String searchString) {
        this.type = type;
        this.searchString = searchString;
    }

    //NOTE: The UDP version of this section (below) is different than the older
    // TCP veison and WILL cause problems
    //		if the txt encoding of the type in question is outside the first 7 bits
    // (ascii) and the
    //		text encoding is different..

    // returns Vector of Strings
    public List<String> getObjectFromTransaction(Transaction transaction) throws IOException {
        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            List<String> searchResults = new ArrayList<>();

            for (;;) {
                String searchResult = in.readUTF();

                if (searchResult.equals(""))
                    break;

                searchResults.add(searchResult);
            }

            return searchResults;
        }
    }

    //returns Vector of Strings
    public Object getNullObject() {
        return new ArrayList<>();
    }

    public byte[] getDataForOutgoingPacket() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (var out = new MysterDataOutputStream(byteArrayOutputStream)) {
            out.writeType(type);
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