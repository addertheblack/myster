package com.myster.net.client;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.threedns.ThreeDnsLookupResult;

/**
 * Resolves a Myster server identity through the protocol stack's distributed name service.
 * Implementations may perform multiple transport calls and consult local routing state; callers
 * depend only on the resulting asynchronous lookup capability.
 */
@FunctionalInterface
public interface DnsLookupProtocol {
    /**
     * Resolves the target CID to an identity-verified peer or a bounded terminal result.
     *
     * @param target server identity to resolve
     * @return cancellable asynchronous lookup result
     */
    PromiseFuture<ThreeDnsLookupResult> resolve(ServerCid target);
}
