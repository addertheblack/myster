package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class TypeDatagramClient implements StandardDatagramClientImpl<MysterType[]> {
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
        return DatagramConstants.TYPE_TRANSACTION_CODE;
    }
}