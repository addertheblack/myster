package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import com.myster.client.stream.MysterDataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class TopTenDatagramClient implements StandardDatagramClientImpl<String[]> {
    public static final int TOP_TEN_TRANSACTION_CODE = 10;

    private final MysterType type;

    public TopTenDatagramClient(MysterType type) {
        this.type = type;
    }

    // returns a MysterAddress[]
    public String[] getObjectFromTransaction(Transaction transaction)
            throws IOException {

        MysterDataInputStream in = new MysterDataInputStream(new ByteArrayInputStream(
                transaction.getData()));

        List<String> strings = new ArrayList<>();
        for (;;) {
            String nextString = in.readUTF();

            if (nextString.equals(""))
                break;

            strings.add(nextString);
        }

        String[] addresses = new String[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            addresses[i] = strings.get(i);
        }

        return addresses;
    }

    // returns a MysterAddress[]
    public Object getNullObject() {
        return new String[] {};
    }

    public int getCode() {
        return TOP_TEN_TRANSACTION_CODE;
    }

    public byte[] getDataForOutgoingPacket() {
        return type.getBytes();
    }
}