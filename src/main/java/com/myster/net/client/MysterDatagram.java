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
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.cid.ServerCid;
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

    /**
     * Performs a bidirectional server stats exchange with the remote server.
     * Sends our server stats in the request and receives the remote server's
     * stats in the response. When the parameters include an expected server
     * key, the exchange is encrypted to that key independently of tracker state.
     * Before all local shared file lists are initialized, this operation sends
     * legacy transaction {@code 101} because a complete local card is not yet
     * available; it does not retry {@code 101} after a remote {@code 102}
     * failure.
     *
     * @param params connection parameters including the remote server address
     * @return PromiseFuture containing the remote server's stats
     */
    public PromiseFuture<MessagePak> getBidirectionalServerStats(final ParamBuilder params);

    /**
     * Requests the live public-key/address candidates closest to a target CID.
     * Returned candidates are untrusted hints. Supplying an expected server key
     * authenticates the responding peer, but does not transitively verify any
     * candidate inside that peer's response.
     *
     * @param params remote address and optional expected server key
     * @param target target CID in the unsigned 128-bit ring
     * @param perSideLimit requested candidates per side; non-positive values use the default
     * @return exact, predecessor, and successor candidate groups; an exact
     *         entry's locally derived CID is guaranteed to match {@code target}
     */
    public PromiseFuture<ThreeDnsAddressCandidateSet> findClosest(ParamBuilder params,
                                                                  ServerCid target,
                                                                  int perSideLimit);

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
