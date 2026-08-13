package com.myster.threedns;

import java.util.Objects;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

/**
 * An untrusted public-key/address hint returned by the 3DNS protocol.
 *
 * <p>The CID is always derived from the encoded public key. Possessing this
 * object does not mean that the server at {@link #address()} has proved it owns
 * the key. A consumer must perform an expected-key request before treating the
 * address/key association as trusted tracker state.
 */
public record ThreeDnsAddressCandidate(
    PublicKeyIdentity identity,
    ServerCid cid,
    MysterAddress address
) {
    public ThreeDnsAddressCandidate(PublicKeyIdentity identity, MysterAddress address) {
        this(identity, ServerCid.fromPublicKey(Objects.requireNonNull(identity, "identity").getPublicKey()), address);
    }

    public ThreeDnsAddressCandidate {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(cid, "cid");
        Objects.requireNonNull(address, "address");

        ServerCid derivedCid = ServerCid.fromPublicKey(identity.getPublicKey());
        if (!derivedCid.equals(cid)) {
            throw new IllegalArgumentException("Candidate CID must be derived from its public key");
        }
    }
}
