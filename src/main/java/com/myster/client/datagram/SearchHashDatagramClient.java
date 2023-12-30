package com.myster.client.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.filemanager.FileTypeList;
import com.myster.hash.FileHash;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.transaction.Transaction;
import com.myster.type.MysterType;

public class SearchHashDatagramClient implements StandardDatagramClientImpl<String> {
    public static final int SEARCH_HASH_TRANSACTION_CODE = 150; //There is no
                                                                // UDP version
                                                                // of the first
                                                                // version of
                                                                // get file type
                                                                // list.

    private MysterType type;

    private FileHash[] hashes;

    public SearchHashDatagramClient(MysterType type, FileHash hash) {
        this(type, new FileHash[] { hash });
    }

    public SearchHashDatagramClient(MysterType type, FileHash[] hashes) {
        this.type = type;
        this.hashes = hashes;
    }

    public String getObjectFromTransaction(Transaction transaction)
            throws IOException {
        return FileTypeList.mergePunctuation((new DataInputStream(new ByteArrayInputStream(transaction
                .getData()))).readUTF());
    }

    public String getNullObject() {
        return new String("");
    }

    public byte[] getDataForOutgoingPacket() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            DataOutputStream out = new DataOutputStream(byteArrayOutputStream);

            out.writeInt(type.getAsInt());

            for (int i = 0; i < hashes.length; i++) {
                out.writeUTF(hashes[i].getHashName());

                out.writeShort(hashes[i].getHashLength());

                byte[] byteArray = hashes[i].getBytes();

                out.write(byteArray, 0, byteArray.length);
            }

            out.writeUTF("");
        } catch (IOException ex) {
            throw new com.general.util.UnexpectedException(ex);
        }

        return byteArrayOutputStream.toByteArray();
    }

    public int getCode() {
        return SEARCH_HASH_TRANSACTION_CODE;
    }
}