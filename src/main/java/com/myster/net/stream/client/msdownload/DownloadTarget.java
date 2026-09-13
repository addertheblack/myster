package com.myster.net.stream.client.msdownload;

import java.security.PublicKey;
import java.util.Objects;
import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.net.client.ParamBuilder;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

/**
 * A transfer candidate. An absent remote filename requires a hash lookup on the connected server;
 * no local or previously saved filename is used as a placeholder. Resolving a server identity
 * alone does not establish file availability.
 */
record DownloadTarget(MysterAddress address,
                      MysterType type,
                      Optional<PublicKey> expectedKey,
                      Optional<String> remoteFilename) {
    DownloadTarget {
        Objects.requireNonNull(address);
        Objects.requireNonNull(type);
        Objects.requireNonNull(expectedKey);
        Objects.requireNonNull(remoteFilename);
    }

    /** A candidate whose filename is already known on this endpoint. */
    static DownloadTarget knownFile(MysterFileStub stub) {
        return new DownloadTarget(stub.getMysterAddress(), stub.getType(), Optional.empty(),
                Optional.of(stub.getName()));
    }

    /** A candidate whose remote filename must be obtained using the download's hashes. */
    static DownloadTarget forHash(MysterAddress address, MysterType type, Optional<PublicKey> expectedKey) {
        return new DownloadTarget(address, type, expectedKey, Optional.empty());
    }

    boolean needsHashLookup() {
        return remoteFilename.isEmpty();
    }

    ParamBuilder parameters() {
        ParamBuilder params = new ParamBuilder(address);
        return expectedKey.map(params::withExpectedServerPublicKey).orElse(params);
    }
}
