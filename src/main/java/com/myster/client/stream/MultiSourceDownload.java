package com.myster.client.stream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import com.general.events.EventDispatcher;
import com.general.events.GenericEvent;
import com.general.events.SyncEventDispatcher;
import com.general.events.SyncEventThreadDispatcher;
import com.general.thread.Cancellable;
import com.general.thread.Task;
import com.general.util.Util;
import com.myster.hash.FileHash;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.search.HashCrawlerManager;
import com.myster.search.HashSearchEvent;
import com.myster.search.HashSearchListener;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;

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
    private final FileHash hash; // should be an array at some point!
    private final long fileLength;
    private final HashSearchListener hashSearchListener;
    private final Set<InternalSegmentDownloader> downloaders;
    private final IoFile randomAccessFile; // file downloading to!
    private final int chunkSize;
    private final long initialOffset; // how much was downloaded in a previous
    
    // it's a stack 'cause it
    // doens't matter what data structure so long
    // as add and remove are O(C).
    private final Stack<WorkSegment> unfinishedSegments = new Stack<>();
    private final EventDispatcher dispatcher = new SyncEventDispatcher();
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
        this.hash = partialFile.getHash(com.myster.hash.HashManager.MD5);
        this.fileLength = partialFile.getFileLength();
        this.chunkSize = (int) partialFile.getBlockSize();
        this.partialFile = partialFile;

        MultiSourceUtilities.debug("Block Size : " + partialFile.getBlockSize()
                + " First un-downloaded block " + partialFile.getFirstUndownloadedBlock());
        this.fileProgress = partialFile.getFirstUndownloadedBlock() * partialFile.getBlockSize();
        this.bytesWrittenOut = fileProgress;
        this.initialOffset = fileProgress;

        this.downloaders = new HashSet<InternalSegmentDownloader>();
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
        fireEventAsycronously(createMultiSourceEvent(MultiSourceEvent.START_DOWNLOAD));
        Util.invokeLater(() -> {
            MysterFileStub[] stubs = initialFileStubs;
            if (stubs != null) {
                for (int i = 0; i < stubs.length; i++) {
                    newDownload(stubs[i]);
                }
            }
            crawlerManager.addHash(type, hash, hashSearchListener);
        });
    }

    private synchronized void newDownload(MysterFileStub stub) {
        if (endFlag)
            return;

        if (stub.getMysterAddress() == null)
            return; // cheap hack

        final InternalSegmentDownloader downloader = new InternalSegmentDownloader(controller,
                stub, chunkSize);

        if (downloaders.contains(downloader)) {
            return; // already have a downloader doing this file.
        }

        downloaders.add(downloader);

        fireEventAsycronously(new MSSegmentEvent(MSSegmentEvent.START_SEGMENT, downloader) );
        Util.invokeLater(() -> downloader.start());
    }

    private void fireEventAsycronously(final GenericEvent event) {
        Util.invokeLater(() -> dispatcher.fireEvent(event));
    }

    /**
     * If a segment download has received word that it is now queued it double
     * checks back with this routine to see if it should bother continuing the
     * download.
     */
    private synchronized boolean isOkToQueue() {
        for (InternalSegmentDownloader internalSegmentDownloader : downloaders) {
            if (internalSegmentDownloader.isActive())
                return false;
        }

        return true;
    }

    // removes a download but doesn't stop a download. (so this should be called
    // by downloads that have ended completely.
    private synchronized boolean removeDownload(SegmentDownloader downloader) {
        boolean result = downloaders.remove(downloader);

        fireEventAsycronously(new MSSegmentEvent(MSSegmentEvent.END_SEGMENT, downloader) );

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
        if ((endFlag | isDone()) & (downloaders.size() == 0)) {
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
                fireEventAsycronously(createMultiSourceEvent(MultiSourceEvent.PROGRESS) );
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
        fireEventAsycronously(createMultiSourceEvent(MultiSourceEvent.DONE_DOWNLOAD) );

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


        for (InternalSegmentDownloader internalSegmentDownloader : downloaders) {
            internalSegmentDownloader.flagToEnd();
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
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                return;
            }
        }

    }

    // This method will only be called once right at the end of the download
    private synchronized void endDownloadCleanUp() {
        crawlerManager.removeHash(type, hash, hashSearchListener);

        try {
            randomAccessFile.close();
        } catch (Exception ex) {
            // nothing
        } // assert file is closed

        isDead = true;

        fireEventAsycronously(createMultiSourceEvent(MultiSourceEvent.END_DOWNLOAD) );

        if (isCancelled ) {
            partialFile.done();
            randomAccessFile.getFile().delete();
        }
        
        if (isDone()) {
            done();
        }
    }

    private MultiSourceEvent createMultiSourceEvent(int id) {
        return new MultiSourceEvent(id, initialOffset, bytesWrittenOut, fileLength, isCancelled);
    }

    private class MSHashSearchListener extends HashSearchListener {
        public void startSearch(HashSearchEvent event) {
        }

        public void searchResult(HashSearchEvent event) {
            MysterFileStub stub = event.getFileStub();

            MultiSourceUtilities.debug(stub == null ? "Search Lstnr-> No file with that hash here."
                    : "Search Lstnr-> Got result " + stub);

            if (stub != null) {
                newDownload(stub);
                MultiSourceUtilities
                        .debug("Search Lstnr-> Starting another segment downloader (another source)");
            }
        }

        public void endSearch(HashSearchEvent event) {
        }
    }

    private class ControllerImpl implements Controller {
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

class InternalSegmentDownloader extends MysterThread implements SegmentDownloader {
    // Constants
    public static final int DEFAULT_MULTI_SOURCE_BLOCK_SIZE = 128 * 1024;

    // Static variables
    private static int instanceCounter = 0;

    // Utility variables
    private final EventDispatcher dispatcher = new SyncEventThreadDispatcher();

    // Params
    private final Controller controller;

    private final MysterFileStub stub;

    private final int chunkSize;

    // working variables
    private WorkingSegment workingSegment;

    private MysterSocket socket;

    private int idealBlockSize = DEFAULT_MULTI_SOURCE_BLOCK_SIZE;

    // Utility working variables
    private boolean endFlag = false;

    private boolean deadFlag = false;

    /**
     * is active says if the SegmentDownloader is actively downloading a file or
     * if the download is queued
     */
    private boolean isActive = false;

    public InternalSegmentDownloader(Controller controller, MysterFileStub stub, int chunkSize) {
        super("SegmentDownloader " + (instanceCounter++) + " for " + stub.getName());

        this.stub = stub;
        this.controller = controller;
        this.chunkSize = chunkSize;
    }

    public void addListener(SegmentDownloaderListener listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(SegmentDownloaderListener listener) {
        dispatcher.removeListener(listener);
    }

    public void run() {
        try {
            socket = MysterSocketFactory.makeStreamConnection(stub.getMysterAddress());

            fireEvent(SegmentDownloaderEvent.CONNECTED, 0, 0, 0, 0);

            debug("Work Thread " + getName() + " -> Sending Section Type");
            socket.out.writeInt(com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);

            // throws Exception if bad
            debug("Work Thread " + getName() + " -> Checking Protocol");
            com.myster.client.stream.StandardSuite.checkProtocol(socket.in); 

            debug("Work Thread " + getName() + " -> Doing Header");
            doHeader(socket);

            for (;;) {
                if (endFlag) {
                    return;
                }

                WorkSegment workSegment = controller.getNextWorkSegment(idealBlockSize); // (WorkSegment)workQueue.removeFromHead();

                if (workSegment == null) {
                    return;
                }

                workingSegment = new WorkingSegment(workSegment);

                if (!doWorkBlock(socket, workingSegment)) {
                    return; // this is for kill signals.. there are also exceptions
                }

                workingSegment = null;
            }
        } catch (UnknownProtocolException ex) {
            com.myster.client.stream.StandardSuite.disconnectWithoutException(socket);

            // Well, that's bad.
            debug("Server doesn't understand multi-source download.");
        } catch (IOException ex) {
            ex.printStackTrace(); // this code can handle exceptions so this
            // is
            // really here to see if anything unexpected
            // has occurred
        } finally {
            try {
                socket.close();
            } catch (Exception ex) {
            }

            finishUp();
        }
    }

    // Offset is offset within the file
    // lenght is the length of the current segment
    // progress is the progress through the segment (exclusing offset)
    private void fireEvent(int id, long offset, long progress, int queuePosition, long length) {
        fireEvent(id, offset, progress, queuePosition, length, "");
    }

    private void fireEvent(int id, long offset, long progress, int queuePosition, long length,
            String queuedMessage) {
        dispatcher.fireEvent(new SegmentDownloaderEvent(id, offset, progress, queuePosition,
                length, stub, queuedMessage));
    }

    private void fireEvent(byte type, byte[] data) {
        dispatcher.fireEvent(new SegmentMetaDataEvent(type, data));
    }

    private synchronized void finishUp() {
        deadFlag = true;

        if ((workingSegment == null) || (workingSegment.isDone())) {
            controller.receiveExtraSegments(new WorkSegment[] {});
        } else {
            controller.receiveExtraSegments(new WorkSegment[] { workingSegment
                    .getRemainingWorkSegment() });
        }

        fireEvent(SegmentDownloaderEvent.END_CONNECTION, 0, 0, 0, 0);

        debug("Thread " + getName() + " -> Finished.");

        controller.removeDownload(this);
    }

    private void doHeader(MysterSocket socket) throws IOException {
        socket.out.writeInt(stub.getType().getAsInt());
        socket.out.writeUTF(stub.getName());

        if (socket.in.read() != 1) {
            com.myster.client.stream.StandardSuite.disconnect(socket);

            throw new IOException("Could not find file");
        }
    }

    public boolean isDead() {
        return deadFlag;
    }

    private boolean doWorkBlock(MysterSocket socket, WorkingSegment workingSegment)
            throws IOException {
        debug("Work Thread " + getName() + " -> Reading data "
                + workingSegment.workSegment.startOffset + " " + workingSegment.workSegment.length);

        socket.out.writeLong(workingSegment.workSegment.startOffset);
        socket.out.writeLong(workingSegment.workSegment.length);

        if (workingSegment.workSegment.isEndSignal()) {
            com.myster.client.stream.StandardSuite.disconnect(socket);
            return false;
        }

        debug("Work Thread " + getName() + " -> Reading in QueuePostion");
        for (;;) {
            RobustMML mml = null;

            try {
                mml = new RobustMML(socket.in.readUTF());
            } catch (MMLException ex) {
                throw new IOException("MML String was corrupt.");
            }

            try {
                int queuePosition = Integer.parseInt(mml
                        .get(com.myster.server.stream.MultiSourceSender.QUEUED_PATH));

                String message = mml.get(com.myster.server.stream.MultiSourceSender.MESSAGE_PATH);

                // if (message!=null) progress.setText(message); ///! Stuff for
                // event here

                debug("Queued pos ----> " + queuePosition + " " + message);

                if (queuePosition == 0) {
                    isActive = true; // now we're downloading
                    break; // yippy! on to download!
                }

                isActive = false; // blagh! Looks like we're queued!

                if (!controller.isOkToQueue())
                    throw new IOException("Should not be queued!");

                fireEvent(SegmentDownloaderEvent.QUEUED, 0, 0, queuePosition, 0, message);
            } catch (NumberFormatException ex) {
                throw new IOException("Server sent garble as queue position -> " + mml);
            }

            if (endFlag)
                throw new IOException("Was told to end.");
        }

        fireEvent(SegmentDownloaderEvent.START_SEGMENT, workingSegment.workSegment.startOffset, 0,
                0, workingSegment.workSegment.length);// this isn't in the
        // right
        // place

        long timeTakenToDownloadSegment = 0;
        while (workingSegment.getProgress() < workingSegment.workSegment.length) {
            debug("Work Thread " + getName() + " -> Reading in Type");

            if (socket.in.readInt() != 6669)
                throw new IOException("Client/Server lost sync");

            byte type = (byte) socket.in.read();

            switch (type) {
            case 'd':
                // progress.setText("Starting transfer...");
                long blockLength = socket.in.readLong();

                debug("Work Thread " + getName() + " -> Downloading start");
                final long startTime = System.currentTimeMillis();
                downloadDataBlock(socket, workingSegment, blockLength);
                timeTakenToDownloadSegment += System.currentTimeMillis() - startTime;
                debug("Work Thread " + getName() + " -> Downloading finished");

                break;
            default:
                fireEvent(type, getDataBlock(socket));
                break;
            }
        }
        if (!workingSegment.workSegment.isRecycled) {
            debug("Work Thread " + getName() + " -> Took " + (timeTakenToDownloadSegment / 1000)
                    + "s to download " + (workingSegment.workSegment.length / 1024) + "k");
            idealBlockSize = calculateNextBlockSize(workingSegment.workSegment.length,
                    timeTakenToDownloadSegment);
            debug("Work Thread " + getName() + " -> next block will be " + (idealBlockSize / 1024)
                    + "k");
        }
        fireEvent(SegmentDownloaderEvent.END_SEGMENT, workingSegment.workSegment.startOffset,
                workingSegment.workSegment.startOffset + workingSegment.workSegment.length, 0,
                workingSegment.workSegment.length);

        return true;
    }

    private static int IDEAL_BLOCK_TIME_MS = 60 * 1000;

    private int calculateNextBlockSize(long length, long timeTakenToDownloadSegment) {
        int maxLength = (int) (Math.min(length, Integer.MAX_VALUE / 2) * 2); // get
        // the
        // max
        // packet
        // length accounting
        // for int overflow.
        return (int) Math.min(maxLength, (IDEAL_BLOCK_TIME_MS * length) / timeTakenToDownloadSegment);
    }

    public boolean isActive() {
        return isActive;
    }

    private byte[] getDataBlock(MysterSocket socket) throws IOException {
        byte[] buffer = new byte[(int) socket.in.readLong()];

        socket.in.readFully(buffer);

        return buffer;
    }

    private void downloadDataBlock(MysterSocket socket, WorkingSegment workingSegment, long length)
            throws IOException {
        long bytesDownloaded = 0;

        byte[] buffer = new byte[chunkSize];

        long lastProgressTime = System.currentTimeMillis();
        for (bytesDownloaded = 0; bytesDownloaded < length;) {
            long calcBlockSize = (length - bytesDownloaded < chunkSize ? length - bytesDownloaded
                    : chunkSize);

            if (calcBlockSize != buffer.length) {
                buffer = new byte[(int) calcBlockSize]; // could be made more
            }

            if (endFlag)
                throw new IOException("was asked to end");

            socket.in.readFully(buffer);

            controller.receiveDataBlock(new DataBlock(workingSegment.getCurrentOffset(), buffer));

            bytesDownloaded += calcBlockSize; // this line doesn't throw an IO
            // exception
            workingSegment.addProgress(calcBlockSize);

            if (System.currentTimeMillis() - lastProgressTime > 100) {
                fireEvent(SegmentDownloaderEvent.DOWNLOADED_BLOCK,
                        workingSegment.workSegment.startOffset, workingSegment.getProgress(), 0,
                        workingSegment.workSegment.length); // nor this.
                lastProgressTime = System.currentTimeMillis();
            }
        }
    }

    public void flagToEnd() {
        endFlag = true;

        try {
            socket.close();
        } catch (Exception ex) {
        }
    }

    public void end() {
        flagToEnd();

        try {
            join();
        } catch (InterruptedException ex) {
        }
    }

    public int hashCode() {
        return stub.getMysterAddress().hashCode();
    }

    public boolean equals(Object o) {
        InternalSegmentDownloader other = null;
        try {
            other = (InternalSegmentDownloader) o;
        } catch (ClassCastException ex) {
            return false;
        }

        return (stub.getMysterAddress().equals(other.stub.getMysterAddress()));
    }

    private static void debug(String string) {
        MultiSourceUtilities.debug(string);
    }

    /**
     * A workign segment is a work segment with the amount progress throught it.
     */
    private static class WorkingSegment {
        public final WorkSegment workSegment;

        private long progress = 0;

        public WorkingSegment(WorkSegment workSegment) {
            this.workSegment = workSegment;
        }

        public long getProgress() {
            return progress;
        }

        public void addProgress(long amount) {
            this.progress += amount;
        }

        /**
         * Returns the remaining part of this work segment as a WorkSegment;
         * 
         * This call returns a stupid no-op work segment if it's done. Call
         * "isDone" to make sure the segment hasen't been finished before using
         * this method.
         */
        public WorkSegment getRemainingWorkSegment() {
            return new WorkSegment(workSegment.startOffset + progress, workSegment.length
                    - progress);
        }

        public boolean isDone() {
            return (progress == workSegment.length);
        }

        public long getCurrentOffset() {
            return (workSegment.startOffset + progress);
        }
    }
}

// Unsafe object due to lack of immutable arrays.

class DataBlock {
    public final byte[] bytes;

    public final long offset;

    public DataBlock(long offset, byte[] bytes) {
        this.offset = offset;
        this.bytes = bytes;
    }
}

// immutable!
final class WorkSegment {
    public final boolean isRecycled;

    public final long startOffset, length;

    public WorkSegment(long startOffset, long length) {
        this(startOffset, length, false);
    }

    private WorkSegment(long startOffset, long length, boolean isRecycled) {
        this.startOffset = startOffset;
        this.length = length;
        this.isRecycled = isRecycled;
    }

    public boolean isEndSignal() {
        return (startOffset == 0) && (length == 0);
    }

    public WorkSegment recycled(boolean isRecycled) {
        return new WorkSegment(startOffset, length, isRecycled);
    }
}

interface Controller {
    WorkSegment getNextWorkSegment(int requestedSize);

    void receiveExtraSegments(WorkSegment[] workSegments);

    void receiveDataBlock(DataBlock dataBlock);

    boolean removeDownload(SegmentDownloader downloader);

    boolean isOkToQueue(); // returns false if it's not ok to queue.
}
