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

/**
 * Manages a multi-source download from the Myster network. This class coordinates multiple
 * concurrent segment downloaders to download different parts of a file from various sources.
 * 
 * <h3>Download Lifecycle</h3>
 * <ul>
 * <li>Created in paused state (unstarted downloads are considered paused)</li>
 * <li>start() begins downloading and fires startDownload event</li>
 * <li>pause() stops all segment downloaders but preserves state for resumption</li>
 * <li>start() again (resume) restarts downloaders and fires resumeDownload event</li>
 * <li>flagToEnd() or cancel() terminates the download</li>
 * <li>Download completes when all bytes have been written</li>
 * </ul>
 * 
 * <h3>State Flags</h3>
 * <ul>
 * <li><b>isPaused</b> - Download is paused and can be resumed (starts true for unstarted downloads)</li>
 * <li><b>endFlag</b> - Shutdown has been requested; overrides pause state for cancellation</li>
 * <li><b>isCancelled</b> - User has cancelled the download</li>
 * <li><b>isDead</b> - Download has stopped and cleanup is complete (regardless of completion status)</li>
 * <li><b>hasStarted</b> - Download has been started at least once (distinguishes first start from resume)</li>
 * </ul>
 * 
 * <h3>Progress Tracking</h3>
 * <ul>
 * <li><b>fileProgress</b> - Next byte offset for work segment allocation</li>
 * <li><b>bytesWrittenOut</b> - Actual bytes written to disk (tracks real progress)</li>
 * <li><b>initialOffset</b> - Starting offset from partial file (for resume after app restart)</li>
 * </ul>
 * 
 * <h3>Stub Discovery</h3>
 * The download maintains a set of discovered file stubs (sources) that are continuously
 * accumulated via hash crawler searches. When paused, the crawler continues finding sources
 * in the background. On resume, downloaders are recreated from all discovered stubs.
 * 
 * <h3>Thread Safety</h3>
 * All state-modifying methods are synchronized using monitor locks (synchronized methods).
 * Event callbacks may execute on the EDT via Util.invokeLater().
 * 
 * <h3>Termination Semantics</h3>
 * <ul>
 * <li><b>flagToEnd()</b> - Requests async termination (returns immediately)</li>
 * <li><b>end()</b> - Blocks until isDead is true</li>
 * <li>endFlag overrides pause: a paused download can still be cancelled</li>
 * </ul>
 */
public class MultiSourceDownload implements Task, Cancellable {
    private static final long MIN_TIME_BETWEEN_EVENTS = 100;

    public static final int DEFAULT_CHUNK_SIZE = 2 * 1024;
    
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
    private final long initialOffset; // how much was downloaded in a previous session
    private final MSDownloadLocalQueue queue; // never null
    
    // Set of all discovered file stubs (sources) for this file.
    // Continuously accumulated via hash crawler and initial servers.
    // Used to rapidly restart downloaders when resuming after pause.
    private final Set<MysterFileStub> discoveredStubs = new HashSet<>();
    
    // it's a stack 'cause it
    // doesn't matter what data structure so long
    // as add and remove are O(C).
    private final Stack<WorkSegment> unfinishedSegments = new Stack<>();
    private final NewGenericDispatcher<MSDownloadListener> dispatcher =
            new NewGenericDispatcher<>(MSDownloadListener.class, Invoker.EDT_NOW_OR_LATER);
    private final Controller controller = new ControllerImpl();
    private final HashCrawlerManager crawlerManager;
    private final FileMover fileMover;

    // State tracking
    private long fileProgress = 0; // next byte offset for work segment allocation
    private long bytesWrittenOut = 0; // actual bytes written to disk

    // State flags - see class JavaDoc for detailed explanation
    private boolean pausedFlag = true; // starts paused (unstarted downloads are considered paused)
    private boolean hasStartedFlag = false; // true after first start() call
    private boolean cancelledFlag = false; // true when user has cancelled the download
    private boolean endNowFlag = false; // true to tell download to stop (overrides pause)
    private boolean endedFlag = false; // true after cleanup has been called
    private boolean locallyQueuedFlag = false; // true when download is in the local queue

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
                               MSPartialFile partialFile,
                               MSDownloadLocalQueue queue)
            throws IOException {
        this.randomAccessFile = randomAccessFile;
        this.crawlerManager = crawlerManager;
        this.fileMover = fileMover;
        this.queue = queue;
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
     * Stubs are added to the discoveredStubs set and will be used on the first
     * start() call or any subsequent resume.
     * <p>
     * This should be called on the same thread that is starting the download,
     * before the download is started.
     * 
     * @param addresses
     *            to try initially.
     */
    public synchronized void addInitialServers(MysterFileStub[] addresses) {
        for (MysterFileStub stub : addresses) {
            discoveredStubs.add(stub);
        }
    }

    public synchronized void start() {
        // Return early if already running
        if (!pausedFlag && !locallyQueuedFlag) {
            return;
        }

        // Determine if this is first start or resume (before updating
        // hasStarted)
        final boolean isFirstStart = !hasStartedFlag;

        // Fire appropriate event based on whether this is first start or resume
        if (isFirstStart) {
            hasStartedFlag = true;
            dispatcher.fire().startDownload(createStartMultiSourceEvent());
        }

        // If already locally queued, force-start to bump this download to active
        if (locallyQueuedFlag) {
            queue.forceStartDownload(this);
        } else {
            queue.addToQueue(this);
        }
        
        Util.invokeLater(() -> {
            // Register hashes with crawler only on first start
            if (isFirstStart) {
                for (FileHash hash : hashes) {
                    crawlerManager.addHash(type, hash, hashSearchListener);
                }
            }
        });
    }
    
    /**
     * Starts the download directly without going through the queue.
     * This is package-private and should only be called by MSDownloadLocalQueue.
     */
    synchronized void startDirectly() {
        pausedFlag = false;
        locallyQueuedFlag = false;
        
        Util.invokeLater(() -> {
            // Create downloaders from all discovered stubs
            synchronized (this) {
                // Don't create downloaders if we've been paused again or ended
                if (pausedFlag || endNowFlag) {
                    return;
                }
                
                if (endedFlag) {
                    return;
                }

                dispatcher.fire().resumeDownload(createMultiSourceEvent());

                for (MysterFileStub stub : discoveredStubs) {
                    newDownload(stub);
                }
            }

            // If no sources and no hashes, cancel the download
            synchronized (this) {
                if (discoveredStubs.isEmpty() && hashes.length == 0) {
                    flagToEnd();
                }
            }
        });
    }
    
    public synchronized void pause() {
             queue.removeFromQueue(this);
     }
    
    /**
     * Pauses the download directly without going through the queue.
     * This is package-private and should only be called by MSDownloadLocalQueue.
     */
    synchronized void pauseDirectly() {
        // Return early if already paused
        if (pausedFlag) {
            return;
        }
        
        if (endedFlag) {
            return;
        }
        
        pausedFlag = true;
        
        // Flag all segment downloaders to end
        for (SegmentDownloader segmentDownloader : downloaders) {
            segmentDownloader.flagToEnd();
        }
        
        downloaders.clear();
        
        // Fire pause event
        dispatcher.fire().pauseDownload(createMultiSourceEvent());
    }
    
    /**
     * Notifies the download that it has been queued at the given position.
     * This is package-private and should only be called by MSDownloadLocalQueue.
     * 
     * @param queuePosition the position in the queue (1-based)
     */
    synchronized void notifyQueued(int queuePosition) {
        locallyQueuedFlag = true;
        dispatcher.fire().queuedDownload(createQueuedMultiSourceEvent(queuePosition));
    }
    
    /**
     * Notifies the download that it has been removed from the queue (truly paused).
     * This is package-private and should only be called by MSDownloadLocalQueue.
     * Fires a queued event with position -1 to indicate "unqueued".
     */
    synchronized void notifyUnqueued() {
        locallyQueuedFlag = false;
        dispatcher.fire().queuedDownload(createQueuedMultiSourceEvent(-1));
    }

    /** Package Protected for unit tests */
    synchronized void newDownload(MysterFileStub stub) {
        if (endNowFlag)
            return;
        
        if (endedFlag) 
            return;

        if (stub.getMysterAddress() == null)
            return; // cheap hack
        
        // Track this stub as discovered
        discoveredStubs.add(stub);
        
        if (pausedFlag) {
            return;
        }

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
     * @param workingSegment 
     */
    private synchronized boolean isOkToQueue(WorkSegment workSegment) {
        for (SegmentDownloader download : downloaders) {
            if (download.isActive()) {
                receiveExtraSegments(new WorkSegment[] { workSegment });
                
                return false;
            }
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
        if ((endNowFlag || isDone()) && (downloaders.size() == 0)) {
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

        var tempFileProgress = (readLength == 0 ? 0 : fileProgress);
        MultiSourceUtilities.debug("Main Thread -> Adding Work Segment " + tempFileProgress + " "
                + readLength);

        // generate an end signal.
        WorkSegment workSegment = new WorkSegment(tempFileProgress, readLength);

        fileProgress += readLength;
        
        return workSegment;
    }

    private synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
        for (int i = 0; i < workSegments.length; i++) {
            unfinishedSegments.push(workSegments[i]);
        }
        
        // I'm leaving this here because this "Stuff left over but no one to do it"
        // should NEVER happen and when it does download disconnect for no reason
        // before they've d/led everything
//        if (!isDone() && workSegments.length !=0) {
//            for (SegmentDownloader downloader : downloaders) {
//                if (downloader.isActive() && !downloader.isDead()) {
//                    return;
//                }
//            }
//            
//            for (SegmentDownloader downloader : downloaders) {
//                if (!downloader.isActive() || downloader.isDead()) {
//                    System.out.println("Stuff left over but no one to do it " +workSegments.length+ " downloads size: "+ downloaders.size() + " " + downloader.isActive() + " - " + downloader.isDead());
//                }
//            }
//            System.out.println("---");
//        }
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
            flagToEnd();// humm.. maybe the user should be notified of this problem?
            // TODO add some sort of notification about these kinds of error here.
        }
    }

    public synchronized boolean isDead() {
        return endedFlag;
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
        if (endedFlag)
            return;
        cancelledFlag = true;

        if (endNowFlag)
            return; // shouldn't be called twice..

        endNowFlag = true;


        for (SegmentDownloader SegmentDownloader : downloaders) {
            SegmentDownloader.flagToEnd();
        }

        endCheckAndCleanup(); // just in case there was no downloader threads.
    }

    public void cancel() {
        flagToEnd();
    }

    public boolean isCancelled() {
        return cancelledFlag;
    }

    public void end() {
        flagToEnd();

        while (!endedFlag) { // wow, is crap
            try {
                Thread.sleep(1);
            } catch (InterruptedException _) {
                return;
            }
        }

    }

    // This method will only be called once right at the end of the download
    private synchronized void endDownloadCleanUp() {
        if (endedFlag) {
            return;
        }
        
        // endFlag overrides pause state - force unpause
        pausedFlag = false;
        
        for (FileHash hash : hashes) {
            crawlerManager.removeHash(type, hash, hashSearchListener);
        }

        try {
            randomAccessFile.close();
        } catch (Exception _) {
            // nothing
        } // assert file is closed
        
        try {
            partialFile.close();
        } catch (IOException ex) {
            // whatever
        }

        endedFlag = true;
        
        queue.removeFromQueue(this);

        dispatcher.fire().endDownload(createMultiSourceEvent());
        
        if (cancelledFlag ) {
            partialFile.done();
            randomAccessFile.getFile().delete();
        }
        
        if (isDone()) {
            done();
        }
    }

    private MultiSourceEvent createMultiSourceEvent() {
        return new MultiSourceEvent(initialOffset, bytesWrittenOut, fileLength, cancelledFlag);
    }
    
    private StartMultiSourceEvent createStartMultiSourceEvent() {
        return new StartMultiSourceEvent(new MSDownloadControl() {
            @Override
            public void pause() {
                MultiSourceDownload.this.pause();
            }

            @Override
            public void resume() {
                MultiSourceDownload.this.start();
            }
            
            @Override
            public void cancel() {
                MultiSourceDownload.this.cancel();
            }
            
            @Override
            public boolean isPaused() {
                return MultiSourceDownload.this.pausedFlag;
            }
            
            @Override
            public boolean isActive() {
                return !MultiSourceDownload.this.isDead() && !MultiSourceDownload.this.isDone();
            }
            
            @Override
            public boolean isLocallyQueued() {
                return MultiSourceDownload.this.locallyQueuedFlag;
            }
        },initialOffset, bytesWrittenOut, fileLength, cancelledFlag);
    }
    
    private QueuedMultiSourceEvent createQueuedMultiSourceEvent(int queuePosition) {
        return new QueuedMultiSourceEvent(queuePosition, initialOffset, bytesWrittenOut, fileLength, cancelledFlag);
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
        @Override
        public void receiveExtraSegments(WorkSegment... workSegments) {
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
        public boolean isOkToQueue(WorkSegment workingSegment) {
            return MultiSourceDownload.this.isOkToQueue(workingSegment);
        }
    }
}
