package com.myster.threedns;

import java.util.Objects;

import com.myster.identity.Cid128;
import com.myster.identity.Util;
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
    Cid128 cid,
    MysterAddress address
) {
    public ThreeDnsAddressCandidate(PublicKeyIdentity identity, MysterAddress address) {
        this(identity, Util.generateCid(Objects.requireNonNull(identity, "identity").getPublicKey()), address);
    }

    public ThreeDnsAddressCandidate {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(cid, "cid");
        Objects.requireNonNull(address, "address");

        Cid128 derivedCid = Util.generateCid(identity.getPublicKey());
        if (!derivedCid.equals(cid)) {
            throw new IllegalArgumentException("Candidate CID must be derived from its public key");
        }
    }
}
