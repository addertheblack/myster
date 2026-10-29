package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;

class TestDefaultKnownServerSource {
    private final MysterServerPool pool = mock(MysterServerPool.class);
    private final DnsLookupProtocol dns = mock(DnsLookupProtocol.class);
    private final DefaultKnownServerSource source = new DefaultKnownServerSource(pool, dns);
    private final MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress(), 7000);
    private final MysterServer server = mock(MysterServer.class);
    private final ThreeDnsLookupResult lookupResult = mock(ThreeDnsLookupResult.class);
    private final VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
    private ServerCid cid;

    @BeforeEach
    void setUp() throws Exception {
        var identity = new PublicKeyIdentity(KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair().getPublic());
        cid = ServerCid.fromPublicKey(identity.getPublicKey());
        when(peer.cid()).thenReturn(cid);
        when(peer.identity()).thenReturn(identity);
        when(peer.address()).thenReturn(address);
        when(server.getIdentity()).thenReturn(identity);
        when(lookupResult.status()).thenReturn(ThreeDnsLookupResult.Status.EXACT_VERIFIED);
        when(lookupResult.exactPeer()).thenReturn(Optional.of(peer));
        when(dns.resolve(cid)).thenReturn(PromiseFuture.newPromiseFuture(lookupResult));
        when(pool.resolveServer(address)).thenReturn(PromiseFuture.newPromiseFuture(server));
    }

    @Test
    void resolvesCidThrough3DnsAndReturnsTheMatchingPoolServer() throws Exception {
        assertSame(server, source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));
        verify(dns).resolve(cid);
        verify(pool).resolveServer(address);
    }

    @ParameterizedTest
    @EnumSource(value = ThreeDnsLookupResult.Status.class, names = "EXACT_VERIFIED",
            mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonExactResultsWithoutContactingAnotherServer(ThreeDnsLookupResult.Status status) {
        when(lookupResult.status()).thenReturn(status);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));

        assertTrue(failure.getCause() instanceof IOException);
        verifyNoInteractions(pool);
    }

    @Test
    void rejectsAnExactResultForADifferentCid() {
        when(peer.cid()).thenReturn(cid.plusPowerOfTwo(0));

        assertThrows(ExecutionException.class,
                () -> source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));
        verifyNoInteractions(pool);
    }

    @Test
    void rejectsStatsFromADifferentPublicKeyIdentity() {
        when(server.getIdentity()).thenReturn(mock(PublicKeyIdentity.class));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));

        assertTrue(failure.getCause() instanceof IOException);
    }

    @Test
    void cancellationDuring3DnsCancelsLookupWithoutStartingStats() {
        PromiseFuture<ThreeDnsLookupResult> pending = PromiseFuture.newPromiseFuture(_ -> {});
        when(dns.resolve(cid)).thenReturn(pending);

        PromiseFuture<MysterServer> result = source.resolveServerCid(cid);
        result.cancel();

        assertTrue(pending.isCancelled());
        verifyNoInteractions(pool);
    }

    @Test
    void cancellationDuringStatsCancelsTheCallerOwnedStatsRequest() {
        PromiseFuture<MysterServer> pending = PromiseFuture.newPromiseFuture(_ -> {});
        when(pool.resolveServer(address)).thenReturn(pending);

        PromiseFuture<MysterServer> result = source.resolveServerCid(cid);
        result.cancel();

        assertTrue(pending.isCancelled());
    }

    @Test
    void lookupAndStatsFailuresPropagateUnchanged() {
        IOException lookupFailure = new IOException("3DNS transport failed");
        when(dns.resolve(cid)).thenReturn(PromiseFuture.newPromiseFutureException(lookupFailure));
        ExecutionException first = assertThrows(ExecutionException.class,
                () -> source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));
        assertSame(lookupFailure, first.getCause());
        verifyNoInteractions(pool);

        IOException statsFailure = new IOException("No server stats");
        when(dns.resolve(cid)).thenReturn(PromiseFuture.newPromiseFuture(lookupResult));
        when(pool.resolveServer(address)).thenReturn(PromiseFuture.newPromiseFutureException(statsFailure));
        ExecutionException second = assertThrows(ExecutionException.class,
                () -> source.resolveServerCid(cid).get(2, TimeUnit.SECONDS));
        assertSame(statsFailure, second.getCause());
    }

    @Test
    void addressResolutionStillDelegatesDirectlyToThePool() throws Exception {
        assertSame(server, source.resolveServer(address).get(2, TimeUnit.SECONDS));
        verifyNoInteractions(dns);
    }

    @Test
    void resolvesStoredMemberDisplayNamesWithoutNetworkLookup() {
        PublicKeyIdentity identity = peer.identity();
        when(pool.lookupIdentityFromCid(cid)).thenReturn(Optional.of(identity.getPublicKey()));
        when(pool.getCachedMysterServer(identity)).thenReturn(Optional.of(server));
        when(server.getServerName()).thenReturn("Friendly Server");

        assertEquals(Optional.of("Friendly Server"), source.resolveDisplayName(cid));
        verifyNoInteractions(dns);
    }
}
