package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.myster.type.MetadataTypeId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.general.thread.PromiseFuture;
import com.general.util.MapPreferences;
import com.myster.access.AccessList;
import com.myster.access.AddMemberOp;
import com.myster.access.Policy;
import com.myster.access.Role;
import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.TypeDescriptionList;

class TestTypeJoinCoordinator {
    @Test
    void previewRequiresExactPeerPinsItsKeyAndDoesNotImport() throws Exception {
        AccessList accessList = accessList(Optional.empty(), Policy.defaultRestrictive());
        KeyPair bootstrapKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        ServerCid bootstrap = ServerCid.fromPublicKey(bootstrapKey.getPublic());
        TypeJoinUri uri = TypeJoinUri.create(accessList.getMysterType(), bootstrap, new byte[16]);
        MysterAddress address = new MysterAddress(InetAddress.getLoopbackAddress());
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.address()).thenReturn(address);
        when(peer.identity()).thenReturn(new PublicKeyIdentity(bootstrapKey.getPublic()));
        ThreeDnsLookupResult result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.EXACT_VERIFIED);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        DnsLookupProtocol lookup = mock(DnsLookupProtocol.class);
        when(lookup.resolve(bootstrap)).thenReturn(PromiseFuture.newPromiseFuture(result));
        TypeDescriptionList descriptions = mock(TypeDescriptionList.class);
        MysterSocket socket = mock(MysterSocket.class);
        MysterStream stream = mock(MysterStream.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.getAccessList(socket, accessList.getMysterType()))
                .thenReturn(Optional.of(accessList));
        TypeJoinCoordinator coordinator = new TypeJoinCoordinator(lookup, descriptions,
                newServerCid(), new MapPreferences(), stream);

        TypeJoinCoordinator.Preview preview = coordinator.prepare(uri).get(3, TimeUnit.SECONDS);

        assertSame(accessList, preview.accessList());
        ArgumentCaptor<ParamBuilder> params = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(params.capture());
        assertEquals(address, params.getValue().getAddress().orElseThrow());
        assertEquals(bootstrapKey.getPublic(),
                params.getValue().getExpectedServerPublicKey().orElseThrow());
        assertTrue(preview.invitationRequired());
        verify(descriptions, never()).importOrRefreshType(any(), any(Boolean.class));
    }

    @Test
    void approvedResponseImportsOnlyWhenFreshChainProvesMembership() throws Exception {
        ServerCid local = newServerCid();
        AccessList proven = accessList(Optional.of(local), Policy.defaultRestrictive());
        TypeJoinUri uri = TypeJoinUri.create(proven.getMysterType(), newServerCid(), new byte[16]);
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.address()).thenReturn(new MysterAddress(InetAddress.getLoopbackAddress()));
        when(peer.identity()).thenReturn(new PublicKeyIdentity(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic()));
        TypeDescriptionList descriptions = mock(TypeDescriptionList.class);
        MysterSocket socket = mock(MysterSocket.class);
        MysterStream stream = mock(MysterStream.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.redeemTypeInvitation(any(), any(), any(), any()))
                .thenReturn(TypeJoinStatus.APPROVED);
        when(stream.getAccessList(socket, proven.getMysterType())).thenReturn(Optional.of(proven));
        MapPreferences preferences = new MapPreferences();
        TypeJoinCoordinator coordinator = new TypeJoinCoordinator(mock(DnsLookupProtocol.class),
                descriptions, local, preferences, stream);
        TypeJoinCoordinator.Preview preview = new TypeJoinCoordinator.Preview(
                uri, peer, proven, "Private", true);

        assertSame(proven, coordinator.redeem(preview, "secret", false)
                .get(3, TimeUnit.SECONDS));
        verify(descriptions).importOrRefreshType(proven, false);
        assertFalse(coordinator.defaultEnabled());
    }

    @Test
    void successStatusWithoutMembershipProofImportsNothing() throws Exception {
        ServerCid local = newServerCid();
        AccessList unproven = accessList(Optional.empty(), Policy.defaultRestrictive());
        TypeJoinUri uri = TypeJoinUri.create(
                unproven.getMysterType(), newServerCid(), new byte[16]);
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.address()).thenReturn(new MysterAddress(InetAddress.getLoopbackAddress()));
        when(peer.identity()).thenReturn(new PublicKeyIdentity(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic()));
        TypeDescriptionList descriptions = mock(TypeDescriptionList.class);
        MysterSocket socket = mock(MysterSocket.class);
        MysterStream stream = mock(MysterStream.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.redeemTypeInvitation(any(), any(), any(), any()))
                .thenReturn(TypeJoinStatus.APPROVED);
        when(stream.getAccessList(socket, unproven.getMysterType()))
                .thenReturn(Optional.of(unproven));
        TypeJoinCoordinator coordinator = new TypeJoinCoordinator(mock(DnsLookupProtocol.class),
                descriptions, local, new MapPreferences(), stream);

        assertThrows(ExecutionException.class, () -> coordinator.redeem(
                new TypeJoinCoordinator.Preview(uri, peer, unproven, "Private", true),
                "secret", true).get(3, TimeUnit.SECONDS));
        verify(descriptions, never()).importOrRefreshType(any(), any(Boolean.class));
    }

    @Test
    void cancellationClosesActivePinnedSocket() throws Exception {
        AccessList accessList = accessList(Optional.empty(), Policy.defaultRestrictive());
        KeyPair bootstrapKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        ServerCid bootstrap = ServerCid.fromPublicKey(bootstrapKey.getPublic());
        TypeJoinUri uri = TypeJoinUri.create(accessList.getMysterType(), bootstrap, new byte[16]);
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.address()).thenReturn(new MysterAddress(InetAddress.getLoopbackAddress()));
        when(peer.identity()).thenReturn(new PublicKeyIdentity(bootstrapKey.getPublic()));
        ThreeDnsLookupResult result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.EXACT_VERIFIED);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        DnsLookupProtocol lookup = mock(DnsLookupProtocol.class);
        when(lookup.resolve(bootstrap)).thenReturn(PromiseFuture.newPromiseFuture(result));
        MysterSocket socket = mock(MysterSocket.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MysterStream stream = mock(MysterStream.class);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(stream.getAccessList(socket, accessList.getMysterType())).thenAnswer(_ -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("interrupted", exception);
            }
            return Optional.of(accessList);
        });
        TypeJoinCoordinator coordinator = new TypeJoinCoordinator(lookup,
                mock(TypeDescriptionList.class), newServerCid(), new MapPreferences(),
                stream);

        PromiseFuture<TypeJoinCoordinator.Preview> operation = coordinator.prepare(uri);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        operation.cancel();
        verify(socket).close();
        release.countDown();
    }

    private static AccessList accessList(Optional<ServerCid> member, Policy policy)
            throws Exception {
        KeyPair writer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair typeIdentity = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        List<AddMemberOp> members = member
                .map(cid -> List.of(new AddMemberOp(cid, Role.MEMBER)))
                .orElseGet(List::of);
        return AccessList.createGenesis(typeIdentity.getPublic(), writer, members, List.of(), policy,
                "Private", "", new String[] {"dat"}, false, MetadataTypeId.GENERIC);
    }

    private static ServerCid newServerCid() throws Exception {
        return ServerCid.fromPublicKey(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic());
    }
}
