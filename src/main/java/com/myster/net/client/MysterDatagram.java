package com.myster.net.client;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.general.thread.PromiseFuture;
import com.general.util.UnexpectedException;
import com.general.util.UnexpectedInterrupt;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePak;
import com.myster.net.datagram.client.PingResponse;
import com.myster.net.datagram.message.MessagePacket;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public interface MysterDatagram {
    public PromiseFuture<String[]> getTopServers(final ParamBuilder params, final MysterType type);


    public PromiseFuture<List<String>> getSearch(final ParamBuilder params,
                                                 final MysterType type,
                                                 final String searchString);

    public PromiseFuture<MessagePacket> sendInstantMessage(ParamBuilder params,
                                                           String msg,
                                                           String reply);

    public PromiseFuture<MysterType[]> getTypes(final ParamBuilder params);

    public PromiseFuture<MessagePak> getServerStats(final ParamBuilder params);

    public PromiseFuture<MessagePak> getFileStats(final MysterFileStub stub);

    public PromiseFuture<String> getFileFromHash(final ParamBuilder params,
                                                 final MysterType type,
                                                 final FileHash hash);

    public PromiseFuture<PingResponse> ping(ParamBuilder params);
    
    public static <T> T cleanResult(PromiseFuture<T> f) throws IOException {
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
}