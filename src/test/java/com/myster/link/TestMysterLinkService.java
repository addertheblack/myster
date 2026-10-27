package com.myster.link;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeListManager;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.*;
import com.myster.search.MysterFileStub;
import com.myster.threedns.*;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;

class TestMysterLinkService {
    private MysterProtocol protocol;
    private MysterStream stream;
    private DnsLookupProtocol dns;
    private FileTypeListManager files;
    private MysterLinkService service;
    private PublicKeyIdentity local;
    private PublicKeyIdentity remote;
    private MysterAddress address;
    private MysterSocket socket;
    private final MysterType type = new MysterType(new byte[16]);

    @BeforeEach void setup() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519");
        local = new PublicKeyIdentity(keys.generateKeyPair().getPublic());
        remote = new PublicKeyIdentity(keys.generateKeyPair().getPublic());
        protocol = mock(MysterProtocol.class);
        stream = mock(MysterStream.class);
        dns = mock(DnsLookupProtocol.class);
        files = mock(FileTypeListManager.class);
        socket = mock(MysterSocket.class);
        address = MysterAddress.createMysterAddress("127.0.0.1:7731");
        when(protocol.getStream()).thenReturn(stream);
        when(protocol.getDnsLookup()).thenReturn(dns);
        when(stream.makeStreamConnection(any(ParamBuilder.class))).thenReturn(socket);
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.of(remote.getPublicKey()));
        var peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.identity()).thenReturn(remote);
        when(peer.address()).thenReturn(address);
        when(peer.cid()).thenReturn(ServerCid.fromPublicKey(remote.getPublicKey()));
        var result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.EXACT_VERIFIED);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        when(dns.resolve(peer.cid())).thenReturn(PromiseFuture.newPromiseFuture(result));
        service = new MysterLinkService(protocol, files, local, () -> 9123);
    }

    private <T> T await(PromiseFuture<T> future) throws Exception { return future.get(3, TimeUnit.SECONDS); }

    @Test void allDestinationsPassTheExactServerAndOpaqueNameWithoutMetadataLookups() throws Exception {
        for (int depth = 0; depth < 3; depth++) {
            var selectedType = depth > 0 ? Optional.of(type) : Optional.<MysterType>empty();
            var name = depth == 2 ? Optional.of("folder/file + 日本語") : Optional.<String>empty();
            var result = await(service.prepare(MysterLinkUri.create(ServerCid.fromPublicKey(remote.getPublicKey()), selectedType, name)));
            assertEquals(selectedType, result.type());
            assertEquals(remote, result.identity());
            assertEquals(name, result.name());
        }
        verifyNoInteractions(stream);
    }

    @Test void closestServerIsNeverUsedAsAnExactOrigin() throws Exception {
        var result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.CLOSEST_VERIFIED);
        when(dns.resolve(any())).thenReturn(PromiseFuture.newPromiseFuture(result));
        var uri = MysterLinkUri.create(ServerCid.fromPublicKey(remote.getPublicKey()), Optional.of(type), Optional.of("file"));
        assertThrows(ExecutionException.class, () -> await(service.prepare(uri)));
        verifyNoInteractions(stream);
    }

    @Test void mismatchedVerifiedCidIsRejected() throws Exception {
        var peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.cid()).thenReturn(ServerCid.fromPublicKey(local.getPublicKey()));
        var result = mock(ThreeDnsLookupResult.class);
        when(result.status()).thenReturn(ThreeDnsLookupResult.Status.EXACT_VERIFIED);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        when(dns.resolve(any())).thenReturn(PromiseFuture.newPromiseFuture(result));
        var uri = MysterLinkUri.create(ServerCid.fromPublicKey(remote.getPublicKey()), Optional.empty(), Optional.empty());
        assertThrows(ExecutionException.class, () -> await(service.prepare(uri)));
    }

    @Test void remoteCopyUsesTheAuthenticatedCidAndPinsTheConnectionWithoutMetadata() throws Exception {
        var stub = new MysterFileStub(address, type, "file");
        var link = await(service.createRemote(stub, Optional.of(remote.getPublicKey())));
        assertEquals(ServerCid.fromPublicKey(remote.getPublicKey()), link.server());
        assertEquals(Optional.of("file"), link.name());
        assertEquals(Optional.of(type), link.type());
        var params = ArgumentCaptor.forClass(ParamBuilder.class);
        verify(stream).makeStreamConnection(params.capture());
        assertEquals(Optional.of(remote.getPublicKey()), params.getValue().getExpectedServerPublicKey());
        verify(stream, never()).getFileStats(any(), any());
        verify(socket).close();
        when(socket.getAuthenticatedPeerKey()).thenReturn(Optional.empty());
        assertThrows(ExecutionException.class, () -> await(service.createRemote(stub, Optional.empty())));
    }

    @Test void localResolutionUsesCurrentPortAndSavedIndexWithoutDns(@TempDir Path root) throws Exception {
        Path path = Files.writeString(root.resolve("local"), "content");
        FileItem item = new FileItem(root, path);
        when(files.isShared(type)).thenReturn(true);
        when(files.getFileItem(type, "local")).thenReturn(item);
        var link = MysterLinkUri.create(ServerCid.fromPublicKey(local.getPublicKey()), Optional.of(type), Optional.of("local"));
        var target = await(service.prepare(link));
        assertEquals(9123, target.address().getPort());
        assertEquals(Optional.of("local"), target.name());
        assertEquals(link.toString(), service.createLocal(type, "local").toString());
        var port = new java.util.concurrent.atomic.AtomicInteger(9123);
        var localService = new MysterLinkService(protocol, files, local, port::get);
        port.set(9134);
        assertEquals(9134, await(localService.prepare(link)).address().getPort());
        Files.delete(path);
        assertThrows(IOException.class, () -> service.createLocal(type, "local"));
        assertThrows(ExecutionException.class, () -> await(service.prepare(link)));
        verifyNoInteractions(dns, stream);
    }

    @Test void localServerAndTypeNavigationNeedNoFileIndex() throws Exception {
        for (var selectedType : java.util.List.of(Optional.<MysterType>empty(), Optional.of(type))) {
            var link = MysterLinkUri.create(ServerCid.fromPublicKey(local.getPublicKey()), selectedType, Optional.empty());
            assertEquals(selectedType, await(service.prepare(link)).type());
        }
        verifyNoInteractions(files, dns, stream);
    }
}
