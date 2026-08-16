package com.myster.threedns;

import java.util.Objects;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.ParamBuilder;

/**
 * Performs identity-verifying 3DNS queries over the existing datagram API.
 *
 * <p>The useful {@code FIND_CLOSEST} exchange is also the proof operation: a
 * successful result means the responder recovered the one-time response key
 * from a request encrypted to the candidate's advertised public key. Returned
 * neighbor candidates are not transitively verified.
 */
public final class ThreeDnsPeerClient {
    private final MysterDatagram datagram;

    public ThreeDnsPeerClient(MysterDatagram datagram) {
        this.datagram = Objects.requireNonNull(datagram, "datagram");
    }

    /**
     * Queries a candidate using its advertised key as the required responder
     * identity.
     *
     * <p>Cancelling the returned future cancels the underlying UDP transaction.
     * Timeout, cancellation, decryption failure, and malformed replies never
     * produce a verified responder.
     *
     * @param peer untrusted address/public-key candidate to query
     * @param target CID whose neighbors are requested
     * @param perSideLimit requested candidates per side; non-positive values
     *        retain the datagram protocol's default behavior
     * @return the verified responder and its still-untrusted routing hints
     */
    public PromiseFuture<ThreeDnsVerifiedQueryResult> findClosest(ThreeDnsAddressCandidate peer,
                                                                   ServerCid target,
                                                                   int perSideLimit) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(target, "target");

        ParamBuilder params = new ParamBuilder(peer.address())
                .withExpectedServerPublicKey(peer.identity().getPublicKey());
        PromiseFuture<ThreeDnsAddressCandidateSet> query =
                datagram.findClosest(params, target, perSideLimit);

        return PromiseFuture.newPromiseFuture(context -> {
            context.trackForCancellation(query);
            query.addSynchronousCallback(result -> {
                if (result.isCancelled()) {
                    context.cancel();
                } else if (result.isException()) {
                    context.setException(result.getException());
                } else {
                    context.setResult(new ThreeDnsVerifiedQueryResult(
                            new VerifiedThreeDnsPeer(peer), result.getResult()));
                }
            });
        });
    }
}
