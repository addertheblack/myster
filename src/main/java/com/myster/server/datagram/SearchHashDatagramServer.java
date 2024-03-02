package com.myster.server.datagram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import com.myster.client.stream.MysterDataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.net.BadPacketException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

public class SearchHashDatagramServer implements TransactionProtocol {
    public static final int SEARCH_HASH_TRANSACTION_CODE = com.myster.client.datagram.SearchHashDatagramClient.SEARCH_HASH_TRANSACTION_CODE;

    public int getTransactionCode() {
        return SEARCH_HASH_TRANSACTION_CODE;
    }

    public void transactionReceived(TransactionSender sender, Transaction transaction, Object transactionObject) throws BadPacketException {
        try {
            MysterDataInputStream in = new MysterDataInputStream(
                    new ByteArrayInputStream(transaction.getData()));

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOutputStream);

            MysterType type = new MysterType(in.readInt()); //type

            FileHash md5Hash = null;

            for (;;) { //get hash name, get hash length, get hash data until
                // hashname is ""
                String hashType = in.readUTF();
                if (hashType.equals("")) {
                    break;
                }
                int lengthOfHash = 0xffff & in.readShort();

                byte[] hashBytes = new byte[lengthOfHash];
                in.readFully(hashBytes, 0, hashBytes.length);

                if (hashType.equalsIgnoreCase(com.myster.hash.HashManager.MD5)) {
                    md5Hash = SimpleFileHash.buildFileHash(hashType, hashBytes);
                }
            }

            com.myster.filemanager.FileItem file = null;

            if (md5Hash != null) {
                file = com.myster.filemanager.FileTypeListManager.getInstance().getFileFromHash(
                        type, md5Hash);
            }

            if (file == null) {
                out.writeUTF("");
            } else {
                out.writeUTF(file.getName());
            }

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}