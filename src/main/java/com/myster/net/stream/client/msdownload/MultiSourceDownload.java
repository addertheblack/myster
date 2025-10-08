package com.myster.net.stream.client.msdownload;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Cancellable;
import com.general.thread.Invoker;
import com.general.thread.Task;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.search.HashCrawlerManager;
import com.myster.search.HashSearchListener;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

/*
 * Things to do
 * 
 * hook up queued message to something.
 *  
 */
public class MultiSourceDownload implements Task, Cancellable {
    private static final long MIN_TIME_BETWEEN_EVENTS = 100;
    
    static IoFile toIoFile(RandomAccessFile accessFile, File file) {
        return new IoFile() {
            @Override
            public void seek(long pos) throws IOException {
                accessFile.seek(pos);
            }

            @Override
            public void write(byte[] b) throws IOException {
                accessFile.write(b);
            }

            @Override
            public void close() throws IOException {
                accessFile.close();
            }

            @Override
            public File getFile() {
                return file;
            }
            
            
        };
    }
    
    private final MSPartialFile partialFile;
    private final MysterType type;
    private final FileHash[] hashes; // should be an array at some point!
    private final long fileLength;
    private final HashSearchListener hashSearchListener;
    private final Set<SegmentDownloader> downloaders;
    private final IoFile randomAccessFile; // file downloading to!
    private final int chunkSize;
    private final long initialOffset; // how much was downloaded in a previous
    
    // it's a stack 'cause it
    // doens't matter what data structure so long
    // as add and remove are O(C).
    private final Stack<WorkSegment> unfinishedSegments = new Stack<>();
    private final NewGenericDispatcher<MSDownloadListener> dispatcher =
            new NewGenericDispatcher<>(MSDownloadListener.class, Invoker.EDT_NOW_OR_LATER);
    private final Controller controller = new ControllerImpl(); // is self
    private final HashCrawlerManager crawlerManager;
    private final FileMover fileMover;

    // session
    private long fileProgress = 0; // for work segments
    private long bytesWrittenOut = 0; // to know how much of the file has been
    private MysterFileStub[] initialFileStubs;

    // this is true when the user has asked that the download be cancelled.
    private boolean isCancelled = false;
    private boolean endFlag = false; // this is set to true to tell MSSource to stop.
    private boolean isDead = false; // is set to true if cleanUp() has been called

    public static final int DEFAULT_CHUNK_SIZE = 2 * 1024;

    private long lastProgress;
    interface IoFile {
        void seek(long pos) throws IOException;
        void write(byte[] b) throws IOException;
        void close() throws IOException;
        File getFile();
        

        default void writeBlock(long location, byte[] bytes) throws IOException {
            seek(location);
            write(bytes);
        }
    }
    
    interface FileMover {
        void moveFileToFinalDestination(File sourceFile);
    }

    public MultiSourceDownload(IoFile randomAccessFile,
                               HashCrawlerManager crawlerManager,
                               MSDownloadListener listener,
                               FileMover fileMover,
                               MSPartialFile partialFile)
            throws IOException {
        this.randomAccessFile = randomAccessFile;
        this.crawlerManager = crawlerManager;
        this.fileMover = fileMover;
        this.type = partialFile.getType();
        this.hashes = partialFile.getFileHashes();
        this.fileLength = partialFile.getFileLength();
        this.chunkSize = (int) partialFile.getBlockSize();
        this.partialFile = partialFile;

        MultiSourceUtilities.debug("Block Size : " + partialFile.getBlockSize()
                + " First un-downloaded block " + partialFile.getFirstUndownloadedBlock());
        this.fileProgress = partialFile.getFirstUndownloadedBlock() * partialFile.getBlockSize();
        this.bytesWrittenOut = fileProgress;
        this.initialOffset = fileProgress;

        this.downloaders = new HashSet<SegmentDownloader>();
        this.hashSearchListener = new MSHashSearchListener();

        addListener(listener);
    }

    /**
     * Sets the initial list of servers to try when the download is started.
     * This array is referred to only once, when the multi-source download is
     * first started.
     * <p>
     * This should be called on the same thread that is starting the download,
     * before the download is started.
     * 
     * @param addresses
     *            to try initially.
     */
    public void setInitialServers(MysterFileStub[] addresses) {
        initialFileStubs = new MysterFileStub[addresses.length];
        System.arraycopy(addresses, 0, initialFileStubs, 0, addresses.length);
    }

    public synchronized void start() {
        dispatcher.fire().startDownload(createMultiSourceEvent());
        Util.invokeLater(() -> {
            MysterFileStub[] stubs = initialFileStubs;

            if (stubs != null) {
                for (int i = 0; i < stubs.length; i++) {
                    newDownload(stubs[i]);
                }
            }

            for (FileHash hash : hashes) {
                crawlerManager.addHash(type, hash, hashSearchListener);
            }

            if ((stubs == null || stubs.length == 0) && hashes.length == 0) {
                flagToEnd();
            }
        });
    }

    /** Package Protected for unit tests */
    synchronized void newDownload(MysterFileStub stub) {
        if (endFlag)
            return;

        if (stub.getMysterAddress() == null)
            return; // cheap hack

        final SegmentDownloader downloader = newSegmentDownloader(stub, controller);

        if (downloaders.contains(downloader)) {
            return; // already have a downloader doing this file.
        }

        downloaders.add(downloader);

        dispatcher.fire().startSegmentDownloader(new MSSegmentEvent(downloader));
        downloader.start();
    }

    /** Protected for unit tests */
    protected SegmentDownloader newSegmentDownloader(MysterFileStub stub, Controller controller) {
        return new InternalSegmentDownloader(controller,
                                             MysterSocketFactory::makeStreamConnection,
                                             stub,
                                             chunkSize);
    }

    /**
     * If a segment download has received word that it is now queued it double
     * checks back with this routine to see if it should bother continuing the
     * download.
     */
    private synchronized boolean isOkToQueue() {
        for (SegmentDownloader SegmentDownloader : downloaders) {
            if (SegmentDownloader.isActive())
                return false;
        }

        return true;
    }

    // removes a download but doesn't stop a download. (so this should be called
    // by downloads that have ended completely.
    private synchronized boolean removeDownload(SegmentDownloader downloader) {
        boolean result = downloaders.remove(downloader);

        dispatcher.fire().endSegmentDownloader(new MSSegmentEvent(downloader) );

        endCheckAndCleanup(); // check to see if cleanupNeeds to be called.

        return result;
    }

    /**
     * call this every time you think you might have done something that has
     * stopped the download (ie: every time a download has been removed or the
     * download has been canceled.) The routines checks to see if the download
     * and all helper thread have stopped and if it has it calls the final
     * cleanup routine
     */
    private synchronized boolean endCheckAndCleanup() {
        if ((endFlag || isDone()) && (downloaders.size() == 0)) {
            endDownloadCleanUp();

            return true;
        }

        return false;
    }

    private synchronized WorkSegment getNextWorkSegment(int requestedBlockSize) {
        if (unfinishedSegments.size() > 0)
            return unfinishedSegments.pop().recycled(true);

        final int multiBlockSize = Math.max(1, requestedBlockSize / DEFAULT_CHUNK_SIZE)
                * DEFAULT_CHUNK_SIZE; // quick round off to nearest chunk.

        long readLength = (fileLength - fileProgress > multiBlockSize ? multiBlockSize : fileLength
                - fileProgress);

        MultiSourceUtilities.debug("Main Thread -> Adding Work Segment " + fileProgress + " "
                + readLength);

        // generate an end signal.
        WorkSegment workSegment = new WorkSegment((readLength == 0 ? 0 : fileProgress), readLength);

        fileProgress += readLength;

        return workSegment;
    }

    private synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
        for (int i = 0; i < workSegments.length; i++) {
            unfinishedSegments.push(workSegments[i]);
        }
    }

    private synchronized void receiveDataBlock(DataBlock dataBlock) {
        try {
            randomAccessFile.seek(dataBlock.offset);

            randomAccessFile.write(dataBlock.bytes);

            bytesWrittenOut += dataBlock.bytes.length;

            // ON really fast downloads we don't want to overload the event
            // thread with trivial
            // events
            if (System.currentTimeMillis() - lastProgress > MIN_TIME_BETWEEN_EVENTS) {
                lastProgress = System.currentTimeMillis();
                dispatcher.fire().progress(createMultiSourceEvent());
            }

            partialFile.setBit(dataBlock.offset / chunkSize);
        } catch (IOException ex) {
            ex.printStackTrace();
            flagToEnd();// humm.. maybe the user should be notified of this problem?
            // TODO add some sort of notification about these kinds of error here.
        }
    }

    public synchronized boolean isDead() {
        return isDead;
    }

    /**
     * returns true if all of the file has been downloaded
     */
    public synchronized boolean isDone() {
        return (bytesWrittenOut == fileLength);
    }

    // call when download has completed successfully.
    private synchronized void done() {
        dispatcher.fire().doneDownload(createMultiSourceEvent());

        partialFile.done();

        fileMover.moveFileToFinalDestination(randomAccessFile.getFile());
    }

    // if is synchronized will cause deadlocks
    public void addListener(MSDownloadListener listener) {
        dispatcher.addListener(listener);
    }

    // if is synchronized will cause deadlocks
    public void removeListener(MSDownloadListener listener) {
        dispatcher.removeListener(listener);
    }

    public synchronized void flagToEnd() {
        if (isDead)
            return;
        isCancelled = true;

        if (endFlag)
            return; // shouldn't be called twice..

        endFlag = true;


        for (SegmentDownloader SegmentDownloader : downloaders) {
            SegmentDownloader.flagToEnd();
        }

        endCheckAndCleanup(); // just in case there was no downloader threads.
    }

    public void cancel() {
        flagToEnd();
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void end() {
        flagToEnd();

        while (!isDead) { // wow, is crap
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                return;
            }
        }

    }

    // This method will only be called once right at the end of the download
    private synchronized void endDownloadCleanUp() {
        for (FileHash hash : hashes) {
            crawlerManager.removeHash(type, hash, hashSearchListener);
        }

        try {
            randomAccessFile.close();
        } catch (Exception ex) {
            // nothing
        } // assert file is closed

        isDead = true;

        dispatcher.fire().endDownload(createMultiSourceEvent());

        if (isCancelled ) {
            partialFile.done();
            randomAccessFile.getFile().delete();
        }
        
        if (isDone()) {
            done();
        }
    }

    private MultiSourceEvent createMultiSourceEvent() {
        return new MultiSourceEvent(initialOffset, bytesWrittenOut, fileLength, isCancelled);
    }

    private class MSHashSearchListener implements HashSearchListener {
        public void searchResult(MysterFileStub stub) {

            MultiSourceUtilities.debug(stub == null ? "Search Lstnr-> No file with that hash here."
                    : "Search Lstnr-> Got result " + stub);

            if (stub != null) {
                newDownload(stub);
                MultiSourceUtilities
                        .debug("Search Lstnr-> Starting another segment downloader (another source)");
            }
        }
    }

    class ControllerImpl implements Controller {
        /*
         * (non-Javadoc)
         * 
         * @see com.myster.client.stream.Controller#getNextWorkSegment(int)
         */
        public WorkSegment getNextWorkSegment(int requestedSize) {
            return MultiSourceDownload.this.getNextWorkSegment(requestedSize);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.myster.client.stream.Controller#receiveExtraSegments(com.myster.client.stream.WorkSegment[])
         */
        public void receiveExtraSegments(WorkSegment[] workSegments) {
            MultiSourceDownload.this.receiveExtraSegments(workSegments);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.myster.client.stream.Controller#receiveDataBlock(com.myster.client.stream.DataBlock)
         */
        public void receiveDataBlock(DataBlock dataBlock) {
            MultiSourceDownload.this.receiveDataBlock(dataBlock);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.myster.client.stream.Controller#removeDownload(com.myster.client.stream.SegmentDownloader)
         */
        public boolean removeDownload(SegmentDownloader downloader) {
            return MultiSourceDownload.this.removeDownload(downloader);
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.myster.client.stream.Controller#isOkToQueue()
         */
        public boolean isOkToQueue() {
            return MultiSourceDownload.this.isOkToQueue();
        }
    }
}
