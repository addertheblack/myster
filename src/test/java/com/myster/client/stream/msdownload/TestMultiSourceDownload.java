
package com.myster.client.stream.msdownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.myster.client.stream.msdownload.MultiSourceDownload.FileMover;
import com.myster.client.stream.msdownload.MultiSourceDownload.IoFile;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.identity.Identity;
import com.myster.net.MysterAddress;
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
        assertEquals(0, nextWorkSegment.startOffset);
        assertEquals(200, nextWorkSegment.length);

        controller.receiveExtraSegments(new WorkSegment(0, 1), new WorkSegment(3, 5));

        WorkSegment nextWorkSegment2 = controller.getNextWorkSegment(200);
        assertEquals(nextWorkSegment2.startOffset, 3);
        assertEquals(5, nextWorkSegment2.length);
        assertTrue(nextWorkSegment2.recycled);

        WorkSegment nextWorkSegment3 = controller.getNextWorkSegment(200);
        assertEquals(nextWorkSegment3.startOffset, 0);
        assertEquals(1, nextWorkSegment3.length);
        assertTrue(nextWorkSegment3.recycled);

        WorkSegment nextWorkSegment4 = controller.getNextWorkSegment(1);
        assertTrue(nextWorkSegment4.isEndSignal());
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
                .startDownload(Mockito.any(MultiSourceEvent.class));
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
                .startDownload(Mockito.any(MultiSourceEvent.class));
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
            public void startDownload(MultiSourceEvent event) {

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
            public void endDownload(MultiSourceEvent event) {

            }

            @Override
            public void doneDownload(MultiSourceEvent event) {

            }
        });

        // use mockito to stub FileMover
        mover = Mockito.mock(FileMover.class);

        // stub MSPartialFile
        MSPartialFile partialFile = MSPartialFile.create(MysterAddress.createMysterAddress("127.0.0.1"),
                                                         "testFilename",
                                                         File.createTempFile("test", suffix + ".p"),
                                                         new MysterType(identity.getMainIdentity().get().getPublic()),
                                                         2048,
                                                         fileHashes,
                                                         fileLength);

        // stub the newDownload() using mockito's spy method
        download = new MultiSourceDownload(file, manager, listener, mover, partialFile) {
            @Override
            protected SegmentDownloader newSegmentDownloader(MysterFileStub stub,
                                                             Controller controller) {
                return new FakeSegmentDownloader(controller);
            }
        };
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

            controller.receiveDataBlock(new DataBlock(0, new byte[(int)nextWorkSegment.length]));

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