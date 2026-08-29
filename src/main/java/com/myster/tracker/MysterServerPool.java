/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.tracker;

import java.security.PublicKey;
import java.util.Optional;
import java.util.function.Consumer;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;

/**
 * This class exists to make sure that if a server is listed under many
 * categories (ie it's a good MPG3 server as well as being just excellent in
 * ROMS) that no additional Memory is wasted listing the server TWICE.. It also
 * cuts down on the number of pings a very good server receives.
 */
public interface MysterServerPool {
    /**
     * ONLY UNIT TESTS!
     */
    void suggestAddress(String address);

    Optional<MysterIdentity> lookupIdentityFromName(ExternalName externalName);

    /**
     * Looks up the RSA public key for a server identified by its {@link ServerCid}.
     *
     * <p>Returns {@code Optional<PublicKey>} — not {@code Optional<MysterIdentity>} — because
     * only servers with a {@link PublicKeyIdentity} have a {@code ServerCid} in the first place.
     * To obtain a {@link MysterIdentity} for use with
     * {@link #getCachedMysterServer(MysterIdentity)}, wrap the result:
     * <pre>
     *   pool.lookupIdentityFromCid(cid)
     *       .map(PublicKeyIdentity::new)
     *       .flatMap(pool::getCachedMysterServer)
     *       .map(MysterServer::getServerName);
     * </pre>
     *
     * @param cid the 128-bit truncated SHA-256 identity hash of the server's RSA public key
     * @return the server's RSA public key, or empty if not found in the pool
     */
    Optional<PublicKey> lookupIdentityFromCid(ServerCid cid);

    /**
     * Finds currently usable public-key identities nearest to a CID. The exact
     * result is present only when the target CID maps to an up server. Left
     * results are predecessor-side candidates and right results are
     * successor-side candidates. Sparse sides return fewer entries than
     * requested.
     *
     * @param target target CID in the unsigned 128-bit ring
     * @param perSideLimit requested maximum number of left and right
     *        candidates; non-positive values use the pool default
     */
    IdentityNeighborSet findClosestByCid(ServerCid target, int perSideLimit);

    /**
     * @return The MysterServer for this address assuming it's already in the cache. Empty Optional otherwise.
     */
    Optional<MysterServer> getCachedMysterServer(MysterIdentity identity);

    Optional<MysterServer> getCachedMysterServer(MysterAddress address);

    /**
     * @return true if the MysterServer is in the cache, false otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterServer(MysterAddress)}
     */
    boolean existsInPool(MysterIdentity identity);

    /**
     * @return true if the MysterServer is in the cache, false otherwise. Danger of race condition
     * between the check and the call to {@link MysterServerPool#getCachedMysterServer(MysterAddress)}
     */
    boolean existsInPool(MysterAddress address);

    /**
     * When a new MysterServer is discovered the MysterServerPool is so excited it
     * has to tell anyone who will listen
     *
     * @param listener when a new server has just been discovered
     */
    void addPoolListener(MysterPoolListener listener);

    void removePoolListener(MysterPoolListener listener);

    /**
     * Only useful when {@link MysterTypeServerList} are loading. There's a brief time when the {@link MysterServerPool}
     * has been loaded but no MysterTypeServerList have been loaded. In order to stop everything from being GCed we
     * keep a hard link to EVERYTHING initially. Call this when MysterTypeServerLists are done loading.
     */
    void clearHardLinks();

    /**
     * Iterate through all known servers
     */
    void forEach(Consumer<MysterServer> consumer);

    /**
     * Explicitly checks a user-entered address and completes with the exact server inserted or
     * refreshed in this pool. Concurrent requests for the same address share in-flight work.
     * Unlike passive suggestions, an explicit request retries addresses in the dead-address
     * cache. Identity is derived only from the returned server stats; failures complete the
     * future exceptionally and remain eligible for a later explicit retry.
     *
     * @param address resolved address explicitly requested by the user
     * @return future for the pool-owned refreshed server
     */
    PromiseFuture<MysterServer> resolveServer(MysterAddress address);

    /**
     * Call this method if we've received a ping from that server.
     * Note this method only does something if the address is a LAN address.
     *
     * @param address to check
     * @deprecated use {@link #suggestAddress(MysterAddress, PublicKeyIdentity)}
     */
    @Deprecated
    void suggestAddress(MysterAddress address);

    /**
     * Suggests an untrusted address/identity pair and verifies that association
     * with an expected-key server-stats exchange before adding it to pool state.
     *
     * @param address advertised server address
     * @param identity public-key identity, and therefore derived CID, claimed
     *        for that address
     */
    void suggestAddress(MysterAddress address, PublicKeyIdentity identity);

    void receivedDownNotification(MysterAddress address);
}
