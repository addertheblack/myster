package com.myster.threedns;

import com.myster.cid.ServerCid;

/** Supplies one immutable, target-specific set of initial 3DNS candidates. */
@FunctionalInterface
interface ThreeDnsSeedProvider {
    ThreeDnsAddressCandidateSet candidatesFor(ServerCid target, int perSideLimit);
}
