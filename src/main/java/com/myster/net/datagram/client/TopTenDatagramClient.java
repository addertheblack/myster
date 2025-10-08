package com.myster.net.datagram.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
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

        try (MysterDataInputStream in =
                new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
            List<String> strings = new ArrayList<>();
            for (;;) {
                String nextString = in.readUTF();

                if (nextString.equals(""))
                    break;

                strings.add(nextString);
            }

            in.close();

            return strings.toArray(new String[]{});
        }
    }

    public int getCode() {
        return TOP_TEN_TRANSACTION_CODE;
    }

    public byte[] getDataForOutgoingPacket() {
        var byteStream = new ByteArrayOutputStream();
        try (final var out = new MysterDataOutputStream(byteStream )) {
            out.writeType(type);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        
        return byteStream.toByteArray();
    }
}