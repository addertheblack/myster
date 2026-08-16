package com.myster.threedns;

import java.util.Objects;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.PublicKeyIdentity;

/**
 * Immutable evidence that a 3DNS peer completed an expected-key operation.
 *
 * <p>The peer's CID is derived from its public key. Verification applies to the
 * completed operation at this address; it does not assert that the peer is
 * honest, remains reachable, or can be contacted safely without authenticating
 * the expected key again.
 *
 * <p>Construction is package-private so decoded wire hints cannot promote
 * themselves without passing through the 3DNS verification path.
 */
public final class VerifiedThreeDnsPeer {
    private final PublicKeyIdentity identity;
    private final ServerCid cid;
    private final MysterAddress address;

    VerifiedThreeDnsPeer(ThreeDnsAddressCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        identity = candidate.identity();
        cid = candidate.cid();
        address = candidate.address();
    }

    public PublicKeyIdentity identity() {
        return identity;
    }

    public ServerCid cid() {
        return cid;
    }

    public MysterAddress address() {
        return address;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof VerifiedThreeDnsPeer other)) {
            return false;
        }
        return identity.equals(other.identity)
                && cid.equals(other.cid)
                && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, cid, address);
    }

    @Override
    public String toString() {
        return "VerifiedThreeDnsPeer[identity=" + identity
                + ", cid=" + cid
                + ", address=" + address + "]";
    }
}
