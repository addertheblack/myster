package com.myster.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.myster.client.stream.MysterDataOutputStream;
import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.net.BadPacketException;
import com.myster.server.stream.NotInitializedException;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

public class ServerStatsDatagramServer implements TransactionProtocol {
    private static final Logger LOGGER = Logger.getLogger(ServerStatsDatagramServer.class.getName());
    
    public static final int SERVER_STATS_TRANSACTION_CODE = com.myster.client.datagram.ServerStatsDatagramClient.SERVER_STATS_TRANSACTION_CODE;
    
    private final Supplier<String> getIdentity;
    private final Identity identity;
    private final Supplier<Integer> getPort;
    private final FileTypeListManager fileManager;

    public ServerStatsDatagramServer(Supplier<String> getIdentity, Supplier<Integer> getPort, Identity identity, FileTypeListManager fileManager) {
        this.getPort = getPort;
        this.getIdentity = getIdentity;
        this.identity = identity;
        this.fileManager = fileManager;
    }

    @Override
    public int getTransactionCode() {
        return SERVER_STATS_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(byteOutputStream)) {
            out.writeUTF("" + com.myster.server.stream.ServerStats
                    .getMMLToSend(getIdentity.get(), getPort.get(), identity, fileManager));

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   Transaction.NO_ERROR));

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        } catch (NotInitializedException exception) {
            // nothing..
            LOGGER.info("Could not reply server stats, file manager is not inited yet" + exception);
        }
    }
}