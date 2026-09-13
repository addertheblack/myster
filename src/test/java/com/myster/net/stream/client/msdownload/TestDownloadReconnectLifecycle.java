package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.general.thread.AsyncContext;
import com.general.thread.PromiseFuture;
import com.myster.cid.ServerCid;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.net.MysterAddress;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.net.client.MysterStream;
import com.myster.search.HashCrawlerManager;
import com.myster.search.HashSearchListener;
import com.myster.search.MysterFileStub;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.type.MysterType;

class TestDownloadReconnectLifecycle {
    @TempDir Path directory;
    private MSPartialFile partial;
    private Harness download;
    private MultiSourceDownload.IoFile io;
    private final HashCrawlerManager crawler = mock(HashCrawlerManager.class);
    private final BlockingQueue<ControlledSegment> segments = new LinkedBlockingQueue<>();
    private final MysterType type = new MysterType(new byte[16]);
    private final FileHash[] hashes = {SimpleFileHash.buildFromHexString("md5", "00".repeat(16))};

    private void create(DnsLookupProtocol dns) throws Exception {
        partial = MSPartialFile.create(MysterAddress.createMysterAddress("127.0.0.1:6669"),
                "reconnect-" + UUID.randomUUID(), directory.toFile(), type, 1, hashes, 12);
        io = mock(MultiSourceDownload.IoFile.class);
        when(io.getFile()).thenReturn(directory.resolve("payload.i").toFile());
        download = new Harness(io, dns);
        download.addInitialServers(new MysterFileStub[] {
                new MysterFileStub(MysterAddress.createMysterAddress(partial.getServerAddress()),
                        type, partial.getFilename())});
    }

    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T result = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(result);
        return result;
    }

    @AfterEach void cleanup() throws Exception {
        if (download != null) {
            download.pauseDirectly();
            download.cancel();
        }
        if (partial != null) {
            partial.done();
            partial.sourceStore().delete();
        }
    }

    @Test void directAttemptAndSavedCidRecoveryAreIndependentAndQueueGated() throws Exception {
        BlockingQueue<ServerCid> queries = new LinkedBlockingQueue<>();
        AtomicReference<AsyncContext<ThreeDnsLookupResult>> answer = new AtomicReference<>();
        PromiseFuture<ThreeDnsLookupResult> lookup = PromiseFuture.newPromiseFuture(answer::set);
        create(cid -> { queries.add(cid); return lookup; });
        PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        ServerCid cid = ServerCid.fromPublicKey(key);
        partial.sourceStore().record(cid);
        assertEquals(List.of(cid), partial.sourceStore().read());
        download.start(); // Mock queue has not admitted it yet.
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(queries.isEmpty());
        assertTrue(segments.isEmpty());
        download.startDirectly();
        ControlledSegment direct = take(segments);
        assertTrue(direct.target.expectedKey().isEmpty());
        assertTrue(direct.target.remoteFilename().isEmpty(), "Restored hash targets must not carry a stale name");
        assertEquals(cid, take(queries)); // No need to wait for the direct transfer.
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.cid()).thenReturn(cid);
        when(peer.identity()).thenReturn(new PublicKeyIdentity(key));
        when(peer.address()).thenReturn(MysterAddress.createMysterAddress("127.0.0.1:7777"));
        ThreeDnsLookupResult result = mock(ThreeDnsLookupResult.class);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        answer.get().setResult(result);
        ControlledSegment recovered = take(segments);
        assertEquals(Optional.of(key), recovered.target.expectedKey());
        assertTrue(recovered.target.needsHashLookup(), "DNS success still requires a file-hash query");
        assertTrue(recovered.target.remoteFilename().isEmpty(), "A resolved server has no known remote filename");
        assertEquals(7777, recovered.target.address().getPort());
    }

    @Test void successfulBlocksPersistCidAndOldSegmentCannotWriteAfterResume() throws Exception {
        create(cid -> PromiseFuture.newPromiseFutureException(new IOException("No route")));
        download.startDirectly();
        ControlledSegment old = take(segments);
        ServerCid cid = new ServerCid(new byte[16]);
        old.controller.receiveDataBlock(new DataBlock(0, new byte[] {1}), old, Optional.of(cid));
        old.controller.receiveDataBlock(new DataBlock(1, new byte[] {2}), old, Optional.of(cid));
        assertEquals(List.of(cid), partial.sourceStore().read());
        assertEquals(0, partial.getFirstUndownloadedBlock()); // Only two bits, not a whole mask byte.
        download.pauseDirectly();
        download.startDirectly();
        ControlledSegment current = take(segments);
        assertThrows(IOException.class, () -> old.controller.receiveDataBlock(
                new DataBlock(2, new byte[] {3}), old, Optional.of(cid)));
        old.controller.removeDownload(old);
        // Old/new segments compare equal by endpoint, but old cleanup must not remove current.
        assertTrue(current.controller.claimSource(current, cid));
        current.controller.receiveDataBlock(new DataBlock(2, new byte[] {3}), current, Optional.of(cid));
        assertEquals(List.of(cid), partial.sourceStore().read());
    }

    @Test void pausedDiscoveryRetainsHashAndRecoveryCandidatesForResume() throws Exception {
        AtomicReference<AsyncContext<ThreeDnsLookupResult>> answer = new AtomicReference<>();
        PromiseFuture<ThreeDnsLookupResult> lookup = PromiseFuture.newPromiseFuture(answer::set);
        BlockingQueue<ServerCid> queries = new LinkedBlockingQueue<>();
        create(cid -> { queries.add(cid); return lookup; });
        PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        ServerCid cid = ServerCid.fromPublicKey(key);
        partial.sourceStore().record(cid);
        download.start();
        SwingUtilities.invokeAndWait(() -> {});
        var listener = org.mockito.ArgumentCaptor.forClass(HashSearchListener.class);
        verify(crawler).addHash(eq(type), eq(hashes[0]), listener.capture());
        download.startDirectly();
        ControlledSegment direct = take(segments);
        assertEquals(cid, take(queries));
        DownloadSourceRecovery recovery = download.currentRecovery();

        download.pauseDirectly();
        assertTrue(direct.stopped);
        answer.get().setResult(exact(key, "127.0.0.1:7777"));
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        assertFalse(lookup.isCancelled(), "Pause must let recovery finish");
        MysterFileStub hashResult = new MysterFileStub(
                MysterAddress.createMysterAddress("127.0.0.1:7778"), type, "remote-name");
        listener.getValue().searchResult(hashResult);
        verify(crawler, never()).removeHash(any(), any(), any());
        assertTrue(segments.isEmpty(), "Neither discovery path may start a paused transfer");

        download.startDirectly();
        List<ControlledSegment> resumed = List.of(take(segments), take(segments), take(segments));
        ControlledSegment recovered = resumed.stream()
                .filter(segment -> segment.target.address().getPort() == 7777).findFirst().orElseThrow();
        assertEquals(Optional.of(key), recovered.target.expectedKey());
        assertTrue(recovered.target.needsHashLookup());
        ControlledSegment fromHash = resumed.stream()
                .filter(segment -> segment.target.address().equals(hashResult.getMysterAddress()))
                .findFirst().orElseThrow();
        assertEquals(Optional.of("remote-name"), fromHash.target.remoteFilename());
    }

    @Test void resumeKeepsPendingRecoveryAndAcceptsItsLaterSuggestion() throws Exception {
        AtomicReference<AsyncContext<ThreeDnsLookupResult>> answer = new AtomicReference<>();
        PromiseFuture<ThreeDnsLookupResult> lookup = PromiseFuture.newPromiseFuture(answer::set);
        BlockingQueue<ServerCid> queries = new LinkedBlockingQueue<>();
        create(cid -> { queries.add(cid); return lookup; });
        PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        ServerCid cid = ServerCid.fromPublicKey(key);
        partial.sourceStore().record(cid);
        download.startDirectly();
        take(segments);
        assertEquals(cid, take(queries));
        DownloadSourceRecovery recovery = download.currentRecovery();

        download.pauseDirectly();
        download.startDirectly();
        take(segments);
        SwingUtilities.invokeAndWait(() -> {});
        assertSame(recovery, download.currentRecovery());
        assertFalse(recovery.isDone());
        answer.get().setResult(exact(key, "127.0.0.1:7777"));
        ControlledSegment recovered = take(segments);
        assertEquals(Optional.of(key), recovered.target.expectedKey());
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        assertFalse(lookup.isCancelled());
        assertTrue(queries.isEmpty(), "Resume must not duplicate pending lookups");
    }

    @Test void resumeRetriesCompletedRecovery() throws Exception {
        DnsLookupProtocol dns = mock(DnsLookupProtocol.class);
        when(dns.resolve(any())).thenAnswer(_ ->
                PromiseFuture.newPromiseFutureException(new IOException("No route")));
        create(dns);
        ServerCid cid = new ServerCid(new byte[16]);
        partial.sourceStore().record(cid);
        download.startDirectly();
        take(segments);
        SwingUtilities.invokeAndWait(() -> {});
        DownloadSourceRecovery previous = download.currentRecovery();
        assertTrue(previous.awaitTermination(5, TimeUnit.SECONDS));

        download.pauseDirectly();
        download.startDirectly();
        take(segments);
        SwingUtilities.invokeAndWait(() -> {});
        DownloadSourceRecovery retry = download.currentRecovery();
        assertNotSame(previous, retry);
        assertTrue(retry.awaitTermination(5, TimeUnit.SECONDS));
        verify(dns, times(2)).resolve(cid);
        assertEquals(List.of(cid), partial.sourceStore().read());
    }

    @Test void cancelWhilePausedStopsRecoveryAndIgnoresLateResults() throws Exception {
        AtomicReference<AsyncContext<ThreeDnsLookupResult>> answer = new AtomicReference<>();
        PromiseFuture<ThreeDnsLookupResult> lookup = PromiseFuture.newPromiseFuture(answer::set);
        BlockingQueue<ServerCid> queries = new LinkedBlockingQueue<>();
        create(cid -> { queries.add(cid); return lookup; });
        PublicKey key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        ServerCid cid = ServerCid.fromPublicKey(key);
        partial.sourceStore().record(cid);
        download.startDirectly();
        take(segments);
        assertEquals(cid, take(queries));
        DownloadSourceRecovery recovery = download.currentRecovery();

        download.pauseDirectly();
        download.cancel();
        assertTrue(recovery.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(lookup.isCancelled());
        answer.get().setResult(exact(key, "127.0.0.1:7777"));
        assertTrue(download.isDead());
        assertTrue(segments.isEmpty());
    }

    private static ThreeDnsLookupResult exact(PublicKey key, String address) throws IOException {
        VerifiedThreeDnsPeer peer = mock(VerifiedThreeDnsPeer.class);
        when(peer.cid()).thenReturn(ServerCid.fromPublicKey(key));
        when(peer.identity()).thenReturn(new PublicKeyIdentity(key));
        when(peer.address()).thenReturn(MysterAddress.createMysterAddress(address));
        ThreeDnsLookupResult result = mock(ThreeDnsLookupResult.class);
        when(result.exactPeer()).thenReturn(Optional.of(peer));
        return result;
    }

    @Test void failedPayloadWriteDoesNotRecordSource() throws Exception {
        create(cid -> PromiseFuture.newPromiseFutureException(new IOException("No route")));
        download.startDirectly();
        ControlledSegment segment = take(segments);
        doThrow(new IOException("disk full")).when(io).write(any(byte[].class));
        assertThrows(IOException.class, () -> segment.controller.receiveDataBlock(
                new DataBlock(0, new byte[] {1}), segment, Optional.of(new ServerCid(new byte[16]))));
        assertTrue(partial.sourceStore().read().isEmpty());
    }

    @Test void restartPreservesBitmapAndRecoversSavedSources() throws Exception {
        BlockingQueue<ServerCid> queries = new LinkedBlockingQueue<>();
        DnsLookupProtocol dns = cid -> {
            queries.add(cid);
            return PromiseFuture.newPromiseFutureException(new IOException("No route yet"));
        };
        create(dns);
        ServerCid cid = new ServerCid(new byte[16]);
        download.startDirectly();
        ControlledSegment segment = take(segments);
        for (int i = 0; i < 8; i++) {
            segment.controller.receiveDataBlock(new DataBlock(i, new byte[] {(byte) i}), segment, Optional.of(cid));
        }
        download.pauseDirectly();
        partial.close();
        partial.sourceStore().close();
        Path mask = MultiSourceUtils.getIncomingDirectory().toPath().resolve(partial.getFilename() + ".p");
        byte[] before = java.nio.file.Files.readAllBytes(mask);
        partial = MSPartialFile.recreate(mask.toFile());
        download = new Harness(io, dns);
        download.startDirectly();
        assertEquals(cid, take(queries));
        assertEquals(8, partial.getFirstUndownloadedBlock());
        assertEquals(8, download.new ControllerImpl().getNextWorkSegment(1).startOffset());
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(mask));
        assertEquals(List.of(cid), partial.sourceStore().read());
    }

    @Test void completionRemovesServerListAfterWrites() throws Exception {
        create(cid -> PromiseFuture.newPromiseFutureException(new IOException("No route")));
        download.startDirectly();
        ControlledSegment segment = take(segments);
        ServerCid cid = new ServerCid(new byte[16]);
        for (int i = 0; i < 12; i++) {
            segment.controller.receiveDataBlock(new DataBlock(i, new byte[] {(byte) i}), segment, Optional.of(cid));
        }
        segment.controller.removeDownload(segment);
        Path incoming = MultiSourceUtils.getIncomingDirectory().toPath();
        assertFalse(java.nio.file.Files.exists(incoming.resolve(partial.getFilename() + ".p")));
        assertFalse(java.nio.file.Files.exists(incoming.resolve(partial.getFilename() + ".s")));
    }

    private class Harness extends MultiSourceDownload {
        Harness(IoFile file, DnsLookupProtocol dns) throws IOException {
            super(file, crawler, mock(MSDownloadListener.class),
                    mock(FileMover.class), partial, mock(MSDownloadLocalQueue.class), mock(MysterStream.class), dns);
        }
        @Override protected SegmentDownloader newSegmentDownloader(DownloadTarget target, Controller controller) {
            return new ControlledSegment(target, controller);
        }

        // Wait for actual worker/callback completion without adding a production test hook.
        synchronized DownloadSourceRecovery currentRecovery() throws ReflectiveOperationException {
            var field = MultiSourceDownload.class.getDeclaredField("recovery");
            field.setAccessible(true);
            return (DownloadSourceRecovery) field.get(this);
        }
    }

    private class ControlledSegment implements SegmentDownloader {
        final DownloadTarget target;
        final Controller controller;
        boolean stopped;
        ControlledSegment(DownloadTarget target, Controller controller) {
            this.target = target;
            this.controller = controller;
        }
        public void start() { segments.add(this); }
        public void flagToEnd() { stopped = true; }
        public boolean isDead() { return stopped; }
        public boolean isActive() { return !stopped; }
        public void addListener(SegmentDownloaderListener listener) {}
        public void removeListener(SegmentDownloaderListener listener) {}
        @Override public int hashCode() { return target.address().hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof TestDownloadReconnectLifecycle.ControlledSegment segment
                    && target.address().equals(segment.target.address());
        }
    }
}
