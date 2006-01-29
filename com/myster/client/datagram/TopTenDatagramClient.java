package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Vector;

import com.myster.net.MysterAddress;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class TopTenDatagramClient implements StandardDatagramClientImpl {
    public static final int TOP_TEN_TRANSACTION_CODE = 10;

    private final MysterType type;

    public TopTenDatagramClient(MysterType type) {
        this.type = type;
    }

    // returns a MysterAddress[]
    public Object getObjectFromTransaction(Transaction transaction)
            throws IOException {

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                transaction.getData()));

        Vector strings = new Vector(100, 100);
        for (;;) {
            String nextString = in.readUTF();

            if (nextString.equals(""))
                break;

            strings.addElement(nextString);
        }

        String[] addresses = new String[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            addresses[i] = (String) strings.elementAt(i);
        }

        return addresses;
    }

    // returns a MysterAddress[]
    public Object getNullObject() {
        return new MysterAddress[] {};
    }

    public int getCode() {
        return TOP_TEN_TRANSACTION_CODE;
    }

    public byte[] getDataForOutgoingPacket() {
        return type.getBytes();
    }
}