package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class TypeDatagramClient implements StandardDatagramClientImpl<MysterType[]> {
    public static final int TYPE_TRANSACTION_CODE = 74; //There is no UDP
                                                        // version of the first
                                                        // version of get file
                                                        // type list.

    //NOTE: The UDP version of this section (below) is different than the older
    // TCP veison and WILL cause problems
    //		if the txt encoding of the type in question is outside the first 7 bits
    // (ascii) and the
    //		text encoding is different..

    //Returns MysterType[]
    public MysterType[] getObjectFromTransaction(Transaction transaction)
            throws IOException {
        MysterDataInputStream in = new MysterDataInputStream(new ByteArrayInputStream(
                transaction.getData()));

        int numberOfTypes = in.readInt();
        MysterType[] mysterTypes = new MysterType[numberOfTypes];

        for (int i = 0; i < numberOfTypes; i++) {
            mysterTypes[i] = in.readType();
        }

        return mysterTypes;
    }

    public byte[] getDataForOutgoingPacket() {
        return new byte[] {};
    }

    public int getCode() {
        return TYPE_TRANSACTION_CODE;
    }
}