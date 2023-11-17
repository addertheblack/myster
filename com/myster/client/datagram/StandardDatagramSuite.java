package com.myster.client.datagram;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.general.thread.CallListener;
import com.general.thread.Future;
import com.general.util.Semaphore;
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

public class StandardDatagramSuite {

    public static String[] getTopServers(final MysterAddress ip, final MysterType type)
            throws IOException {

        return makeBlocking(new BlockingListener<String[]>(ip, new TopTenDatagramClient(type)));
    }

    public static Future<String[]> getTopServers(final MysterAddress ip,
                                                 final MysterType type,
                                                 final CallListener listener)
            throws IOException {
        return doSection(ip, new TopTenDatagramClient(type), listener);
    }

    // Vector of Strings
    public static List<String> getSearch(final MysterAddress ip,
                                         final MysterType type,
                                         final String searchString)
            throws IOException {

        return makeBlocking(new BlockingListener<List<String>>(ip,
                                                               new SearchDatagramClient(type,
                                                                                        searchString)));
    }

    public static Future<List<String>> getSearch(final MysterAddress ip,
                                                 final MysterType type,
                                                 final String searchString,
                                                 final CallListener<List<String>> listener)
            throws IOException {
        return doSection(ip, new SearchDatagramClient(type, searchString), listener);
    }

    public static MysterType[] getTypes(final MysterAddress ip) throws IOException {
        return makeBlocking(new BlockingListener<MysterType[]>(ip, new TypeDatagramClient()));
    }

    public static Future<MysterType[]> getTypes(final MysterAddress ip,
                                                final CallListener<MysterType[]> listener)
            throws IOException {
        return doSection(ip, new TypeDatagramClient(), listener);
    }

    public static RobustMML getServerStats(final MysterAddress ip) throws IOException {
        return (RobustMML) (makeBlocking(new BlockingListener(ip, new ServerStatsDatagramClient())));
    }

    public static Future<RobustMML> getServerStats(final MysterAddress ip,
                                                   final CallListener<RobustMML> listener)
            throws IOException {
        return doSection(ip, new ServerStatsDatagramClient(), listener);
    }

    public static RobustMML getFileStats(final MysterFileStub stub) throws IOException {
        return makeBlocking(new BlockingListener<RobustMML>(stub.getMysterAddress(),
                new FileStatsDatagramClient(stub)));
    }

    public static Future<RobustMML> getFileStats(final MysterFileStub stub,
                                                 final CallListener<RobustMML> listener)
            throws IOException {
        return doSection(stub.getMysterAddress(), new FileStatsDatagramClient(stub), listener);
    }

    public static String getFileFromHash(final MysterAddress ip,
                                         final MysterType type,
                                         final FileHash hash)
            throws IOException {
        return makeBlocking(new BlockingListener<String>(ip, new SearchHashDatagramClient(type, hash)));
    }

    public static <T> Future<T> getFileFromHash(final MysterAddress ip, final MysterType type,
            final FileHash hash, final CallListener<T> listener) throws IOException {
        return doSection(ip, new SearchHashDatagramClient(type, hash), listener);
    }

    private static <T> Future<T> doSection(final MysterAddress address,
            final StandardDatagramClientImpl<T> impl, final CallListener<T> listener) throws IOException {

        final TransactionSocket tsocket = new TransactionSocket(impl.getCode());

        //We need to convert between a generic transaction, listener and a
        //Standard CallListener because call listeners make a good abstract and
        //turn the call into an RMI. cooool eh?
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
                },

                new TransactionListener() { //inline class
                    public void transactionReply(TransactionEvent e) {
                        try {
                            if (DatagramUtilities.dealWithError(e.getTransaction(), listener))
                                return;

                            try {
                                listener.handleResult(impl.getObjectFromTransaction(e
                                        .getTransaction()));
                            } catch (IOException ex) {
                                listener.handleException(new BadPacketException(ex.getMessage()));
                            }
                        } finally {
                            listener.handleFinally();
                        }
                    }

                    public void transactionTimout(TransactionEvent e) {
                        try {
                            listener.handleException(new TimeoutException("Transaction timed out"));
                        } finally {
                            listener.handleFinally();
                        }
                    }

                    public void transactionCancelled(TransactionEvent event) {
                        try {
                            listener.handleCancel();
                        } finally {
                            listener.handleFinally();
                        }
                    }
                });

        // no need to close socket.. all sockets are one-shot.
        // We return it, though, in case the thread wants to cancel the
        // operation.
        return new UdpFuture(tsocket);
    }
    
    // Will return the result of the async operation or null.
    private static <T> T makeBlocking(final BlockingListener<T> passable) throws IOException {
        final Semaphore sem = new Semaphore(0);
        final Object[] resultOrException = new Object[1];

        //This stuff below might look weird but there's a danger of a data race
        // so I want to
        //make sure all my data uses a common monitor.
        //(Actually, I think there is still a data race)
        passable.get(new CallListener<T>() {
            public void handleCancel() {
                throw new IllegalStateException("Cancel was called here! This is not allowed!");
            }

            public void handleResult(T result) {
                resultOrException[0] = result;
            }

            public void handleException(Exception ex) {
                if (!(ex instanceof IOException))
                    throw new IllegalStateException("Exception " + ex
                            + " is not an IOException. Some sort of error has occurred.");

                resultOrException[0] = ex;
            }

            public void handleFinally() {
                sem.signal();
            }

        });

        try {
            sem.getLock();
        } catch (InterruptedException ex) {
            throw new IOException("Interrupted Thread Wait..");
        }

        // resultOrExecption maybe null at this point (if handleCancel() was called or
        // the code threw an exception (like InterruptedException!). Play it safe.
        if (resultOrException[0] instanceof IOException) {
            throw (IOException) resultOrException[0];
        }

        return (T) resultOrException[0];
    }

    private static class BlockingListener<T> {
        final StandardDatagramClientImpl impl;

        final MysterAddress address;

        public BlockingListener(MysterAddress address, StandardDatagramClientImpl<T> impl) {
            this.impl = impl;
            this.address = address;
        }

        public void get(CallListener<T> listener) throws IOException {
            doSection(address, impl, listener);
        }
    }

    // this class isn't really finished. Really the TransactionSocket should be
    // cancellable or something and that's it.
    private static class UdpFuture implements Future {
        private TransactionSocket tsocket;

        private UdpFuture(TransactionSocket tsocket) {
            this.tsocket = tsocket;
        }

        public boolean cancel() {
            return cancel(true);
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return tsocket.cancel();
        }

        public boolean isCancelled() {
            throw new RuntimeException("Operation not supported - lazy programmer Exception.");
        }

        public boolean isDone() {
            throw new RuntimeException("Operation not supported - lazy programmer Exception.");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, java.util.concurrent.TimeoutException {
            throw new IllegalStateException();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException, CancellationException {
            throw new IllegalStateException();
        }
    }
}