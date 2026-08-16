package com.myster.threedns;

import java.util.Objects;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

/**
 * An untrusted public-key/address hint returned by the 3DNS protocol.
 *
 * <p>The CID is always derived from the encoded public key, and construction
 * rejects an explicit CID that does not match that derivation. This local
 * invariant does not prove that the server at {@link #address()} owns the key.
 * A consumer must complete an expected-key operation before treating the
 * address/key association as verified.
 *
 * @param identity advertised public-key identity
 * @param cid CID derived from {@code identity}; an inconsistent value is rejected
 * @param address advertised endpoint for the candidate
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
