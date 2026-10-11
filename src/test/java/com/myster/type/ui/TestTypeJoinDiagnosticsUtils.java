package com.myster.type.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.myster.cid.ServerCid;
import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.myster.net.MysterAddress;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.datagram.DatagramEncryptUtil.DecryptionException;
import com.myster.threedns.ThreeDnsAddressCandidate;
import com.myster.threedns.ThreeDnsAddressCandidateSet;
import com.myster.threedns.ThreeDnsLookupDiagnostics;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;
import com.myster.type.TypeDescriptionList;
import com.myster.type.join.TypeJoinCoordinator;
import com.myster.type.join.TypeJoinUri;

class TestTypeJoinDiagnosticsUtils {
    @Test
    void failedKnownExactBootstrapIncludesAddressCauseAndSnapshotWithoutInvitationCredentials() throws Exception {
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var candidate = new ThreeDnsAddressCandidate(new PublicKeyIdentity(key.getPublic()),
                new MysterAddress(InetAddress.getLoopbackAddress(), 7001));
        var cause = new DecryptionException("response authentication failed", new java.io.IOException("crypto cause"));
        var result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.NO_ROUTE);
        when(result.closestPeer()).thenReturn(Optional.empty());
        when(result.diagnostics()).thenReturn(new ThreeDnsLookupDiagnostics(
                new ThreeDnsAddressCandidateSet(Optional.of(candidate), List.of(), List.of()),
                List.of(new ThreeDnsLookupDiagnostics.QueryAttempt(candidate,
                        ThreeDnsLookupDiagnostics.Outcome.FAILED, Optional.of(cause))), Duration.ofMillis(123)));
        byte[] invitation = new byte[16];
        java.util.Arrays.fill(invitation, (byte) 0xab);
        var uri = TypeJoinUri.parse(TypeJoinUri.create(new MysterType(key.getPublic()), candidate.cid(), invitation)
                + "&code=super-secret");
        var lookup = mock(DnsLookupProtocol.class);
        when(lookup.resolve(uri.bootstrap())).thenReturn(PromiseFuture.newPromiseFuture(result));
        var coordinator = new TypeJoinCoordinator(lookup, mock(TypeDescriptionList.class),
                candidate.cid(), new MapPreferences(), mock(MysterStream.class));
        var failure = assertThrows(ExecutionException.class, () -> coordinator.prepare(uri).get(3, TimeUnit.SECONDS));

        String details = TypeJoinDiagnosticsUtils.details("preview", uri.bootstrap(), failure.getCause());

        assertTrue(TypeJoinDiagnosticsUtils.message(failure.getCause()).contains("No queried server"));
        assertTrue(details.contains("3DNS outcome: NO_ROUTE"));
        assertTrue(details.contains("Usable exact tracker candidate: " + candidate.cid() + " at " + candidate.address()));
        assertTrue(details.contains("Elapsed: 123 ms"));
        assertTrue(details.contains("DecryptionException: response authentication failed"));
        assertTrue(details.contains("IOException: crypto cause"));
        assertFalse(details.contains("super-secret"));
        assertFalse(details.contains(java.util.HexFormat.of().formatHex(invitation)));
    }

    @Test
    void unexpectedFailureShowsExceptionTypeAndRetainsCauseStack() {
        var cause = new IllegalArgumentException("bad internal state");
        var failure = new IllegalStateException("worker bug", cause);
        var bootstrap = new ServerCid(new byte[16]);

        String message = TypeJoinDiagnosticsUtils.message(failure);
        String details = TypeJoinDiagnosticsUtils.details("preview", bootstrap, failure);

        assertTrue(message.contains("Unexpected IllegalStateException"));
        assertTrue(details.contains("Bootstrap CID: " + bootstrap));
        assertTrue(details.contains("Caused by: java.lang.IllegalArgumentException: bad internal state"));
        assertTrue(details.contains(getClass().getName()));
        assertFalse(details.contains("bootstrap could not be found"));
    }
}
