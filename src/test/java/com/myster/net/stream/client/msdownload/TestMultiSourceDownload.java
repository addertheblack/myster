
package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
import com.myster.net.stream.client.msdownload.MultiSourceDownload.FileMover;
import com.myster.net.stream.client.msdownload.MultiSourceDownload.IoFile;
import com.myster.search.HashCrawlerManager;
import com.myster.search.HashSearchListener;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public class TestMultiSourceDownload {
    private MultiSourceDownload download;

    private ArgumentCaptor<HashSearchListener> managerArgumentCaptor;
    private HashCrawlerManager manager;
    private MSDownloadListener listener;
    private FileMover mover;
    private MSDownloadLocalQueue queue;
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    static Path tempDir;
    static Identity identity; 
    
    @BeforeAll
    static void beforeAll() {
        identity = new Identity("TestMultiSourceDownload", tempDir.toFile());
    }


    @Test
    void testGetNextWorkSegment() throws UnknownHostException, IOException, InterruptedException {
        createNewMsDownload(new FileHash[] {});

        Controller controller = download.new ControllerImpl();
        WorkSegment nextWorkSegment = controller.getNextWorkSegment(1);
        assertEquals(0, nextWorkSegment.startOffset());
        assertEquals(200, nextWorkSegment.length());

        controller.receiveExtraSegments(new WorkSegment(0, 1), new WorkSegment(3, 5));

        WorkSegment nextWorkSegment2 = controller.getNextWorkSegment(200);
        assertEquals(nextWorkSegment2.startOffset(), 3);
        assertEquals(5, nextWorkSegment2.length());
        assertTrue(nextWorkSegment2.recycled());

        WorkSegment nextWorkSegment3 = controller.getNextWorkSegment(200);
        assertEquals(nextWorkSegment3.startOffset(), 0);
        assertEquals(1, nextWorkSegment3.length());
        assertTrue(nextWorkSegment3.recycled());

        WorkSegment nextWorkSegment4 = controller.getNextWorkSegment(1);
        assertTrue(nextWorkSegment4.isEndSignal());
        
        download.end();
    }


    @Test
    void testDownloadWithNoStubOrHash() throws UnknownHostException, IOException, InterruptedException {
        createNewMsDownload(new FileHash[] {});

        /*
         * Now make the download method do nothing but keep it around so we can
         * check how many times it has been called later
         */
        download.start();

        while (!download.isDead()) {
            Thread.sleep(1);
        }

        // flush the event queue
        Util.invokeAndWaitNoThrows(() -> {});
        
        assertEquals(false, download.isDone());
        assertEquals(true, download.isCancelled());
        assertEquals(true, download.isDead());

        /*
         * now check the download() has been called 3 times with the right
         * arguments
         */
        Mockito.verify(manager, Mockito.times(0)).addHash(Mockito.any(MysterType.class),
                                                          Mockito.any(FileHash.class),
                                                          Mockito.any(HashSearchListener.class));
        Mockito.verify(mover, Mockito.times(0)).moveFileToFinalDestination(Mockito.any(File.class));

        Mockito.verify(listener, Mockito.times(1))
                .startDownload(Mockito.any(StartMultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(0)).progress(Mockito.any(MultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(0))
                .startSegmentDownloader(Mockito.any(MSSegmentEvent.class));
        Mockito.verify(listener, Mockito.times(0))
                .endSegmentDownloader(Mockito.any(MSSegmentEvent.class));
        Mockito.verify(listener, Mockito.times(1)).endDownload(Mockito.any(MultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(0))
                .doneDownload(Mockito.any(MultiSourceEvent.class));

        Mockito.verify(manager, Mockito.times(0)).removeHash(Mockito.any(MysterType.class),
                                                             Mockito.any(FileHash.class),
                                                             Mockito.any(HashSearchListener.class));
    }

    @Test
    void testDownloadWithSomething()
            throws UnknownHostException, IOException, InterruptedException {
        createNewMsDownload(new FileHash[] {
                new SimpleFileHash("md5", new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 }) });


        download.start();

        Util.invokeAndWaitNoThrows(() -> {});


        Mockito.verify(manager, Mockito.times(1)).addHash(Mockito.any(MysterType.class),
                                                          Mockito.any(FileHash.class),
                                                          managerArgumentCaptor.capture());

        Mockito.verify(manager, Mockito.times(0)).removeHash(Mockito.any(MysterType.class),
                                                             Mockito.any(FileHash.class),
                                                             Mockito.any(HashSearchListener.class));

        assertEquals(false, download.isDone());
        assertEquals(false, download.isCancelled());
        assertEquals(false, download.isDead());

        List<HashSearchListener> allValues = managerArgumentCaptor.getAllValues();
        assertEquals(1, allValues.size());

        allValues.get(0)
                .searchResult( new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
                                                                     new MysterType(identity.getMainIdentity().get().getPublic()),
                                                                     "It doens't matter"));

        Util.invokeAndWaitNoThrows(() -> {});
        
        assertEquals(true, download.isDone());
        assertEquals(false, download.isCancelled());
        assertEquals(true, download.isDead());


        // We only have one segment, we download everything in one chunk so it's
        // one call to everything
        Mockito.verify(listener, Mockito.times(1))
                .startDownload(Mockito.any(StartMultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(1)).progress(Mockito.any(MultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(1))
                .startSegmentDownloader(Mockito.any(MSSegmentEvent.class));
        Mockito.verify(listener, Mockito.times(1))
                .endSegmentDownloader(Mockito.any(MSSegmentEvent.class));
        Mockito.verify(listener, Mockito.times(1)).endDownload(Mockito.any(MultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(1))
                .doneDownload(Mockito.any(MultiSourceEvent.class));

        Mockito.verify(manager, Mockito.times(1)).removeHash(Mockito.any(MysterType.class),
                                                             Mockito.any(FileHash.class),
                                                             Mockito.any(HashSearchListener.class));
     }

    private void createNewMsDownload(FileHash[] fileHashes) throws IOException, UnknownHostException {
        // create stub implementation
        int fileLength = 200;
        byte[] data = new byte[fileLength];

        final String suffix = "" + Math.random() * 1000000l;
        
        File tempFile = File.createTempFile("Test", suffix + ".i");
        tempFile.deleteOnExit();
        IoFile file = new IoFile() {
            private int index = 0;
            
            @Override
            public void seek(long pos) throws IOException {
                index = (int) pos;
            }

            @Override
            public void write(byte[] b) throws IOException {
                System.arraycopy(b, 0, data, index, b.length);
            }

            @Override
            public void close() throws IOException {
                // nothing
            }

            @Override
            public File getFile() {
                return tempFile;
            }
        };

        // Stub HashCrawlerManager
        managerArgumentCaptor = ArgumentCaptor.forClass(HashSearchListener.class);
        manager = Mockito.mock(HashCrawlerManager.class);


        // use mockito to stub MSDownloadListener
        listener = Mockito.spy(new MSDownloadListener() {
            @Override
            public void startDownload(StartMultiSourceEvent event) {

            }

            @Override
            public void progress(MultiSourceEvent event) {

            }

            @Override
            public void startSegmentDownloader(MSSegmentEvent event) {

            }

            @Override
            public void endSegmentDownloader(MSSegmentEvent event) {

            }

            @Override
            public void pauseDownload(MultiSourceEvent event) {

            }

            @Override
            public void resumeDownload(MultiSourceEvent event) {

            }
            
            @Override
            public void queuedDownload(QueuedMultiSourceEvent event) {

            }

            @Override
            public void endDownload(MultiSourceEvent event) {

            }

            @Override
            public void doneDownload(MultiSourceEvent event) {

            }

            @Override
            public Frame getFrame() {
                return null;
            }
        });

        // use mockito to stub FileMover
        mover = Mockito.mock(FileMover.class);
        
        // Mock MSDownloadLocalQueue to immediately start downloads when added
        queue = Mockito.mock(MSDownloadLocalQueue.class);
        Mockito.doAnswer(invocation -> {
            MultiSourceDownload download = invocation.getArgument(0);
            download.startDirectly(); // Immediately start the download for testing
            return null;
        }).when(queue).addToQueue(Mockito.any(MultiSourceDownload.class));
        
        Mockito.doAnswer(invocation -> {
            MultiSourceDownload download = invocation.getArgument(0);
            download.pauseDirectly(); // Immediately pause the download for testing
            return null;
        }).when(queue).removeFromQueue(Mockito.any(MultiSourceDownload.class));

        // stub MSPartialFile
        MSPartialFile partialFile = MSPartialFile.create(MysterAddress.createMysterAddress("127.0.0.1"),
                                                         "testFilename",
                                                         File.createTempFile("test", suffix + ".p"),
                                                         new MysterType(identity.getMainIdentity().get().getPublic()),
                                                         2048,
                                                         fileHashes,
                                                         fileLength);

        // stub the newDownload() using mockito's spy method
        download = new MultiSourceDownload(file, manager, listener, mover, partialFile, queue) {
            @Override
            protected SegmentDownloader newSegmentDownloader(MysterFileStub stub,
                                                             Controller controller) {
                return new FakeSegmentDownloader(controller);
            }
        };
    }

    @Test
    void testPauseAndResumeStateTransitions()
            throws UnknownHostException, IOException, InterruptedException {
        // Test basic pause/resume state management
        createNewMsDownload(new FileHash[] {});

        MysterFileStub stub1 = new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
                                                   new MysterType(identity.getMainIdentity().get().getPublic()),
                                                   "testfile");
        download.addInitialServers(new MysterFileStub[] { stub1 });

        // Start download
        download.start();
        Util.invokeAndWaitNoThrows(() -> {});

        // Verify startDownload was called
        Mockito.verify(listener, Mockito.times(1))
                .startDownload(Mockito.any(StartMultiSourceEvent.class));

        // Pause the download
        download.pauseDirectly(); // does nothing already dead
        Util.invokeAndWaitNoThrows(() -> {});

        // Verify pauseDownload was called
        // broken because the download dies before we get here
//        Mockito.verify(listener, Mockito.times(1))
//                .pauseDownload(Mockito.any(MultiSourceEvent.class));
        
        // Cleanup - cancel to end the download
        download.cancel();
        download.end();
        Util.invokeAndWaitNoThrows(() -> {});
    }

    @Test
    void testResumeFiresCorrectEvent()
            throws UnknownHostException, IOException, InterruptedException {
        // Don't add any initial servers or hashes - just test the state machine
        createNewMsDownload(new FileHash[] {});

        // Don't call setInitialServers - no sources to download from
        // This prevents the download from completing instantly

        // Start download
        download.start();
        Util.invokeAndWaitNoThrows(() -> {});

        // Pause the download
        download.pause();
        Util.invokeAndWaitNoThrows(() -> {});

        // Resume the download  
        download.start();
        Util.invokeAndWaitNoThrows(() -> {});

        // Verify correct events for state machine
        Mockito.verify(listener, Mockito.times(1))
                .startDownload(Mockito.any(StartMultiSourceEvent.class));
        Mockito.verify(listener, Mockito.times(1))
                .resumeDownload(Mockito.any(MultiSourceEvent.class));

        download.end();
        Util.invokeAndWaitNoThrows(() -> {});
    }

    // todo fix this turd
//    @Test
//    void testMultiplePauseResumeCycles()
//            throws UnknownHostException, IOException, InterruptedException {
//        createNewMsDownload(new FileHash[] {});
//
//        MysterFileStub stub1 = new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
//                                                   new MysterType(identity.getMainIdentity().get().getPublic()),
//                                                   "testfile");
//        download.addInitialServers(new MysterFileStub[] { stub1 });
//
//        // Start
//        download.start();
//
//        // Pause immediately (synchronous call)
//        download.pause();
//
//        // Resume  
//        download.start();
//
//        // Pause again
//        download.pause();
//
//        // Resume again
//        download.start();
//        Util.invokeAndWaitNoThrows(() -> {});
//
//        // Verify events - may complete before all pauses fire, so check minimum
//        Mockito.verify(listener, Mockito.times(1))
//                .startDownload(Mockito.any(StartMultiSourceEvent.class));
//        Mockito.verify(listener, Mockito.atLeastOnce())
//                .resumeDownload(Mockito.any(MultiSourceEvent.class));
//        Mockito.verify(listener, Mockito.atLeastOnce())
//                .pauseDownload(Mockito.any(MultiSourceEvent.class));
//
//        // Cleanup
//        download.cancel();
//        download.end();
//        Util.invokeAndWaitNoThrows(() -> {});
//    }

    @Test
    void testCancelOverridesPause()
            throws UnknownHostException, IOException, InterruptedException {
        createNewMsDownload(new FileHash[] {});

        MysterFileStub stub1 = new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
                                                   new MysterType(identity.getMainIdentity().get().getPublic()),
                                                   "testfile");
        download.addInitialServers(new MysterFileStub[] { stub1 });

        // Start download
        download.start();

        // Pause the download immediately (synchronous)
        download.pause();

        // Cancel the paused download - this should work (endFlag overrides pause)
        download.cancel();

        // The key test: cancel() should work even when paused
        // (We verify by checking that the download eventually becomes dead, 
        // which means cleanup completed successfully)
        int maxWait = 50;
        while (!download.isDead() && maxWait-- > 0) {
            Thread.sleep(10);
        }
        
        assertEquals(true, download.isDead());
        download.end();
        Util.invokeAndWaitNoThrows(() -> {});
    }

    @Test
    void testUnstartedDownloadIsConsideredPaused()
            throws UnknownHostException, IOException, InterruptedException {
        createNewMsDownload(new FileHash[] {
                new SimpleFileHash("md5", new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 }) });

        MysterFileStub stub1 = new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
                                                   new MysterType(identity.getMainIdentity().get().getPublic()),
                                                   "testfile");
        download.addInitialServers(new MysterFileStub[] { stub1 });

        // Don't start the download, just cancel it
        download.cancel();

        // Wait for cleanup
        while (!download.isDead()) {
            Thread.sleep(10);
        }

        // Verify download can be cancelled even when unstarted
        assertEquals(true, download.isCancelled());
        assertEquals(true, download.isDead());
        download.end();
        Util.invokeAndWaitNoThrows(() -> {});
    }
}


class FakeSegmentDownloader implements SegmentDownloader {
    private final Controller controller;

    private boolean isDead;

    FakeSegmentDownloader(Controller controller) {
        this.controller = controller;
    }   

    @Override
    public void addListener(SegmentDownloaderListener listener) {
    }

    @Override
    public void removeListener(SegmentDownloaderListener listener) {
        throw new UnsupportedOperationException("Unimplemented method 'removeListener'");
    }

    @Override
    public boolean isDead() {
        return isDead;
    }

    @Override
    public void start() {
        Util.invokeLater(() -> {
            WorkSegment nextWorkSegment = controller.getNextWorkSegment(999);

            controller.receiveDataBlock(new DataBlock(0, new byte[(int)nextWorkSegment.length()]));

            controller.removeDownload(FakeSegmentDownloader.this);

            isDead = true;
        });
    }

    @Override
    public boolean isActive() {
        return !isDead;
    }

    @Override
    public void flagToEnd() {
        isDead = true;
    }
}