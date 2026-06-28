package com.myster.threedns;

import java.util.Objects;

import com.myster.identity.Cid128;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;
import com.myster.tracker.PublicKeyIdentity;

/**
 * One retained 3DNS finger entry. Live entries store a currently up address;
 * restored entries may temporarily hold the server's best known address until
 * normal pool pings decide whether the server is usable.
 */
public record ThreeDnsFingerEntry(
    Cid128 targetCid,
    Cid128 serverCid,
    MysterServer server,
    MysterAddress address,
    Side side,
    long updateTimeMs
) {
    public ThreeDnsFingerEntry {
        Objects.requireNonNull(targetCid);
        Objects.requireNonNull(serverCid);
        Objects.requireNonNull(server);
        Objects.requireNonNull(address);
        Objects.requireNonNull(side);
    }

    public PublicKeyIdentity identity() {
        return (PublicKeyIdentity) server.getIdentity();
    }

    public enum Side {
        LEFT,
        RIGHT
    }
}
