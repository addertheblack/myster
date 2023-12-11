package com.myster.client.datagram;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.general.thread.PromiseFuture;
import com.general.util.UnexpectedException;
import com.general.util.UnexpectedInterrupt;
import com.myster.hash.FileHash;
import com.myster.mml.RobustMML;
import com.myster.net.BadPacketException;
import com.myster.net.DataPacket;
import com.myster.net.MysterAddress;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.net.TimeoutException;
import com.myster.search.MysterFileStub;
import com.myster.transaction.TransactionEvent;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionSocket;
import com.myster.type.MysterType;

class StandardDatagramSuite {

    public static String[] getTopServersBlock(final MysterAddress ip, final MysterType type)
            throws IOException {
        return cleanResult(getTopServers(ip, type));
    }

    public static PromiseFuture<String[]> getTopServers(final MysterAddress ip,
                                                        final MysterType type) {
        return doSection(ip, new TopTenDatagramClient(type));
    }

    // Vector of Strings
    public static List<String> getSearchBlock(final MysterAddress ip,
                                              final MysterType type,
                                              final String searchString)
            throws IOException {
        return cleanResult(getSearch(ip, type, searchString));

    }

    public static PromiseFuture<List<String>> getSearch(final MysterAddress ip,
                                                        final MysterType type,
                                                        final String searchString)
            throws IOException {
        return doSection(ip, new SearchDatagramClient(type, searchString));
    }

    public static MysterType[] getTypesBlock(final MysterAddress ip) throws IOException {
        return cleanResult(getTypes(ip));
    }

    public static PromiseFuture<MysterType[]> getTypes(final MysterAddress ip) throws IOException {
        return doSection(ip, new TypeDatagramClient());
    }

    public static RobustMML getServerStatsBlock(final MysterAddress ip) throws IOException {
        return cleanResult(getServerStats(ip));
    }

    public static PromiseFuture<RobustMML> getServerStats(final MysterAddress ip)
            throws IOException {
        return doSection(ip, new ServerStatsDatagramClient());
    }

    public static RobustMML getFileStatsBlock(final MysterFileStub stub) throws IOException {
        return cleanResult(getFileStats(stub));
    }

    public static PromiseFuture<RobustMML> getFileStats(final MysterFileStub stub)
            throws IOException {
        return doSection(stub.getMysterAddress(), new FileStatsDatagramClient(stub));
    }

    public static String getFileFromHashBlock(final MysterAddress ip,
                                              final MysterType type,
                                              final FileHash hash)
            throws IOException {
        return cleanResult(getFileFromHash(ip, type, hash));
    }

    public static PromiseFuture<String> getFileFromHash(final MysterAddress ip,
                                                       final MysterType type,
                                                       final FileHash hash)
            throws IOException {
        return doSection(ip, new SearchHashDatagramClient(type, hash));
    }

    private static <T> T cleanResult(PromiseFuture<T> f) throws IOException {
        try {
            return f.get();
        } catch (InterruptedException exception) {
            throw new UnexpectedInterrupt(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            if (exception.getCause() instanceof IOException) {

                throw (RuntimeException) exception.getCause();
            }

            throw new UnexpectedException(exception);
        }
    }

    private static <T> PromiseFuture<T> doSection(final MysterAddress address,
            final StandardDatagramClientImpl<T> impl)  {


        return PromiseFuture.<T>newPromiseFuture((context) -> {
            final TransactionSocket tsocket = new TransactionSocket(impl.getCode());

            // We need to convert between a generic transaction, listener and a
            // Standard CallListener because call listeners make a good abstract
            // and
            // turn the call into an RMI. cooool eh?
            tsocket.sendTransaction(new DataPacket() { // inline class
                public MysterAddress getAddress() {
                    return address;
                }

                public byte[] getData() {
                    return impl.getDataForOutgoingPacket();
                }

                public byte[] getBytes() {
                    return getData();
                }

                public byte[] getHeader() {
                    return new byte[] {};
                }
            }, new TransactionListener() { // inline class
                public void transactionReply(TransactionEvent e) {
                    if (DatagramUtilities.dealWithError(e.getTransaction(), context))
                        return;

                    try {
                        context.setResult(impl.getObjectFromTransaction(e.getTransaction()));
                    } catch (IOException ex) {
                        context.setException(new BadPacketException(ex.getMessage()));
                    }
                }

                public void transactionTimout(TransactionEvent e) {
                    context.setException(new TimeoutException("Transaction timed out"));
                }

                public void transactionCancelled(TransactionEvent event) {
                    context.cancel();
                }
            });
        }).useEdt();
    }
}