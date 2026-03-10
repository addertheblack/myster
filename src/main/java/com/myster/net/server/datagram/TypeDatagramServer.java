package com.myster.net.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.general.util.Util;
import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.filemanager.FileTypeListManager;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;
import com.myster.type.MysterType;

/**
 * Server-side datagram handler for the type-listing transaction (section 74 equivalent).
 *
 * <p>Private types whose access list does not permit public listing are filtered out for
 * callers without a verified identity. Membership is checked via {@link AccessEnforcementUtils}.
 */
public class TypeDatagramServer implements TransactionProtocol {
    public static final int NUMBER_OF_FILE_TYPE_TO_RETURN = 100;

    private final FileTypeListManager fileManager;
    private final AccessListReader accessListReader;

    public TypeDatagramServer(FileTypeListManager fileManager, AccessListReader accessListReader) {
        this.fileManager = fileManager;
        this.accessListReader = accessListReader;
    }

    @Override
    public int getTransactionCode() {
        return DatagramConstants.TYPE_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {

        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (MysterDataOutputStream out = new MysterDataOutputStream(byteOutputStream)) {
            MysterType[] allTypes = fileManager.getFileTypeListing();

            List<MysterType> filtered = Util.filter(
                    Arrays.asList(allTypes),
                    t -> AccessEnforcementUtils.isAllowed(t, transaction.callerCid(), accessListReader));

            out.writeInt(filtered.size());
            for (MysterType t : filtered) {
                out.writeType(t);
            }

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   DatagramConstants.NO_ERROR));
        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}

