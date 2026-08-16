package com.myster.threedns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterDatagram;
import com.myster.net.client.ParamBuilder;
import com.myster.tracker.PublicKeyIdentity;

class TestThreeDnsPeerClient {
    private static KeyPair responderKey;
    private static KeyPair returnedKey;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        responderKey = generator.generateKeyPair();
        returnedKey = generator.generateKeyPair();
    }

    @Test
    void successfulQueryUsesExpectedKeyAndVerifiesOnlyResponder() throws Exception {
        MysterDatagram datagram = mock(MysterDatagram.class);
        ThreeDnsAddressCandidate responder = candidate(responderKey, "127.0.0.1:7000");
        ThreeDnsAddressCandidate returned = candidate(returnedKey, "127.0.0.2:7001");
        ThreeDnsAddressCandidateSet candidates = new ThreeDnsAddressCandidateSet(
                Optional.empty(), List.of(returned), List.of());
        when(datagram.findClosest(any(), any(), anyInt()))
                .thenReturn(PromiseFuture.newPromiseFuture(candidates));
        ServerCid target = cid(7);

        ThreeDnsVerifiedQueryResult result =
                new ThreeDnsPeerClient(datagram).findClosest(responder, target, 3).get();

        ArgumentCaptor<ParamBuilder> paramsCaptor = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(datagram).findClosest(paramsCaptor.capture(),
                                     org.mockito.ArgumentMatchers.eq(target),
                                     org.mockito.ArgumentMatchers.eq(3));
        ParamBuilder params = paramsCaptor.getValue();
        assertEquals(responder.address(), params.getAddress().orElseThrow());
        assertEquals(responderKey.getPublic(), params.getExpectedServerPublicKey().orElseThrow());
        assertEquals(responder.identity(), result.responder().identity());
        assertEquals(responder.cid(), result.responder().cid());
        assertEquals(responder.address(), result.responder().address());
        assertSame(candidates, result.candidates());
        assertSame(returned, result.candidates().left().getFirst());
    }

    @Test
    void queryFailureDoesNotProduceVerifiedPeer() {
        MysterDatagram datagram = mock(MysterDatagram.class);
        IOException failure = new IOException("bad encrypted response");
        when(datagram.findClosest(any(), any(), anyInt()))
                .thenReturn(PromiseFuture.newPromiseFutureException(failure));

        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> new ThreeDnsPeerClient(datagram)
                        .findClosest(candidate(responderKey, "127.0.0.1:7000"), cid(1), 2)
                        .get());

        assertSame(failure, thrown.getCause());
    }

    @Test
    void cancellingVerifiedQueryCancelsUnderlyingTransaction() throws Exception {
        MysterDatagram datagram = mock(MysterDatagram.class);
        AtomicReference<AsyncContext<ThreeDnsAddressCandidateSet>> transactionContext =
                new AtomicReference<>();
        PromiseFuture<ThreeDnsAddressCandidateSet> transaction =
                PromiseFuture.newPromiseFuture(transactionContext::set);
        when(datagram.findClosest(any(), any(), anyInt())).thenReturn(transaction);
        PromiseFuture<ThreeDnsVerifiedQueryResult> verified = new ThreeDnsPeerClient(datagram)
                .findClosest(candidate(responderKey, "127.0.0.1:7000"), cid(2), 2);

        assertTrue(verified.cancel(true));

        assertTrue(transaction.isCancelled());
        assertTrue(verified.isCancelled());
        assertFalse(transactionContext.get().setResult(ThreeDnsAddressCandidateSet.empty()));
        assertThrows(CancellationException.class, verified::get);
    }

    @Test
    void upstreamCancellationPropagatesWithoutVerifiedPeer() {
        MysterDatagram datagram = mock(MysterDatagram.class);
        PromiseFuture<ThreeDnsAddressCandidateSet> transaction =
                PromiseFuture.newPromiseFuture(_ -> {});
        when(datagram.findClosest(any(), any(), anyInt())).thenReturn(transaction);
        PromiseFuture<ThreeDnsVerifiedQueryResult> verified = new ThreeDnsPeerClient(datagram)
                .findClosest(candidate(responderKey, "127.0.0.1:7000"), cid(3), 2);

        transaction.cancel();

        assertTrue(verified.isCancelled());
        assertThrows(CancellationException.class, verified::get);
    }

    @Test
    void verifiedPeerHasNoPublicConstructorAndPreservesCandidate() throws Exception {
        ThreeDnsAddressCandidate candidate = candidate(responderKey, "127.0.0.1:7000");
        VerifiedThreeDnsPeer peer = new VerifiedThreeDnsPeer(candidate);

        assertTrue(VerifiedThreeDnsPeer.class.getConstructors().length == 0);
        assertFalse(Modifier.isPublic(
                VerifiedThreeDnsPeer.class.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(candidate.identity(), peer.identity());
        assertEquals(candidate.cid(), peer.cid());
        assertEquals(candidate.address(), peer.address());
    }

    private static ThreeDnsAddressCandidate candidate(KeyPair keyPair, String address) {
        try {
            return new ThreeDnsAddressCandidate(new PublicKeyIdentity(keyPair.getPublic()),
                                                MysterAddress.createMysterAddress(address));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ServerCid cid(int lastByte) {
        byte[] bytes = new byte[ServerCid.LENGTH];
        bytes[bytes.length - 1] = (byte) lastByte;
        return new ServerCid(bytes);
    }
}
