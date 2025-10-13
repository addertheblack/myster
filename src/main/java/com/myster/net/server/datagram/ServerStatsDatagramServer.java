package com.myster.net.server.datagram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.net.datagram.BadPacketException;
import com.myster.net.datagram.DatagramConstants;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.NotInitializedException;
import com.myster.net.stream.server.ServerStats;
import com.myster.transaction.Transaction;
import com.myster.transaction.TransactionProtocol;
import com.myster.transaction.TransactionSender;

/**
 * Server side datagram implementation of Myster server stats connection section.
 */
public class ServerStatsDatagramServer implements TransactionProtocol {
    private static final Logger LOGGER = Logger.getLogger(ServerStatsDatagramServer.class.getName());
    
    private final Supplier<String> getServerName;
    private final Supplier<Integer> getPort;
    private final Identity identity;
    private final FileTypeListManager fileManager;

    public ServerStatsDatagramServer(Supplier<String> getServerName,
                                     Supplier<Integer> getPort,
                                     Identity identity,
                                     FileTypeListManager fileManager) {
        this.getServerName = getServerName;
        this.getPort = getPort;
        this.identity = identity;
        this.fileManager = fileManager;
    }

    @Override
    public int getTransactionCode() {
        return DatagramConstants.SERVER_STATS_TRANSACTION_CODE;
    }

    @Override
    public void transactionReceived(TransactionSender sender,
                                    Transaction transaction,
                                    Object transactionObject)
            throws BadPacketException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(byteOutputStream)) {
            out.writeMessagePack(com.myster.net.stream.server.ServerStats
                    .getServerStatsMessagePack(getServerName.get(),
                                               getPort.get(),
                                               identity,
                                               fileManager));

            sender.sendTransaction(new Transaction(transaction,
                                                   byteOutputStream.toByteArray(),
                                                   DatagramConstants.NO_ERROR));

        } catch (IOException ex) {
            throw new BadPacketException("Bad packet " + ex);
        }
    }
}