package com.myster.client.stream;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import com.myster.hash.FileHash;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.search.HashSearchEvent;
import com.myster.search.HashSearchListener;
import com.myster.search.MultiSourceHashSearch;
import com.myster.search.MysterFileStub;
import com.myster.util.MysterThread;

/*
 * Things to do
 * 
 * hook up queued message to something.
 * 
 */

public class MultiSourceDownload implements Runnable, Controller {
    final MSPartialFile partialFile;

    final MysterFileStub stub; //starter stub (contains first address and
                               // filename and type)

    final FileHash hash; //should be final but can't be...

    final long fileLength; //should be final but can't be...

    final HashSearchListener listener;

    final Hashtable downloaders;

    //File theFile; //file downloading to!
    RandomAccessFile randomAccessFile; //file downloading to!

    final int chunkSize;

    final long initialOffset; //how much was downloaded in a previous session

    long fileProgress = 0; //for work segments

    long bytesWrittenOut = 0; //to know how much of the file has been
                              // downloaded.

    Stack unfinishedSegments = new Stack(); //it's a stack 'cause it doens't

    //matter what data structure so long
    //as add and remove are O(C).

    EventDispatcher dispatcher = new SyncEventDispatcher();

    boolean isCancelled = false;

    boolean endFlag = false; //this is set to true to tell MSSource to stop.

    boolean isDead = false; //is set to true if cleanUp() has been called

    public static final int DEFAULT_CHUNK_SIZE = 2 * 1024;

    // Note top self -> elminate one of the twin constructors.
    public MultiSourceDownload(MysterFileStub stub, FileHash hash,
            long fileLength, MSDownloadListener listener,
            RandomAccessFile randomAccessFile, MSPartialFile partialFile)
            throws IOException {
        this.randomAccessFile = randomAccessFile;
        this.stub = stub;
        this.hash = hash;
        this.fileLength = fileLength;

        this.downloaders = new Hashtable();
        this.listener = new MSHashSearchListener();

        this.partialFile = partialFile; //MSPartialFile.create(stub.getName(),
                                        // new Filehash[]{hash},
                                        // SegmentDownloader.CHUNK_SIZE);

        this.fileProgress = partialFile.getFirstUndownloadedBlock()
                * partialFile.getBlockSize();
        this.bytesWrittenOut = fileProgress;
        this.initialOffset = fileProgress;

        this.chunkSize = (int) partialFile.getBlockSize();

        addListener(listener);
    }

    public MultiSourceDownload(RandomAccessFile randomAccessFile,
            MSDownloadListener listener, MSPartialFile partialFile)
            throws IOException {
        this.randomAccessFile = randomAccessFile;
        this.stub = new MysterFileStub(null, partialFile.getType(), partialFile
                .getFilename());
        this.hash = partialFile.getHash(com.myster.hash.HashManager.MD5);
        this.fileLength = partialFile.getFileLength();
        this.chunkSize = (int) partialFile.getBlockSize();
        this.partialFile = partialFile;

        System.out.println("Block Size : " + partialFile.getBlockSize()
                + " First un-downloaded block "
                + partialFile.getFirstUndownloadedBlock());
        this.fileProgress = partialFile.getFirstUndownloadedBlock()
                * partialFile.getBlockSize();
        this.bytesWrittenOut = fileProgress;
        this.initialOffset = fileProgress;

        this.downloaders = new Hashtable();
        this.listener = new MSHashSearchListener();

        addListener(listener);
    }

    //public void suggestAServer(MysterAddress address) {
    //	newDownload(new MysterFileStub(stub.getName(), stub.getType(), address));
    //}

    //Can be done as a thread or not.
    public void run() {
        dispatcher.fireEvent(new MultiSourceEvent(
                MultiSourceEvent.START_DOWNLOAD, this));

        newDownload(stub);

        MultiSourceHashSearch.addHash(stub.getType(), hash, listener);
    }

    private synchronized void newDownload(MysterFileStub stub) {
        if (endFlag)
            return;

        if (stub.getMysterAddress() == null)
            return; //cheap hack

        InternalSegmentDownloader downloader = new InternalSegmentDownloader(
                this, stub, chunkSize);

        if (downloaders.get(downloader) != null)
            return; //already have a downloader doing this file.

        downloaders.put(downloader, downloader);

        dispatcher.fireEvent(new MSSegmentEvent(MSSegmentEvent.START_SEGMENT,
                downloader));

        downloader.start();
    }

    /**
     * If a segment download has received word that it is now queued it double
     * checks back with this routine to see if it should bother continuing the
     * download.
     */
    public synchronized boolean isOkToQueue() {
        Enumeration enum = downloaders.elements();

        while (enum.hasMoreElements()) {
            InternalSegmentDownloader internalSegmentDownloader = (InternalSegmentDownloader) (enum
                    .nextElement());

            if (internalSegmentDownloader.isActive())
                return false;
        }

        return true;
    }

    //removes a download but doesn't stop a download. (so this should be called
    // by downloads that have ended completely.
    public synchronized boolean removeDownload(SegmentDownloader downloader) {
        boolean result = (downloaders.remove(downloader) != null);

        dispatcher.fireEvent(new MSSegmentEvent(MSSegmentEvent.END_SEGMENT,
                downloader));

        endCheckAndCleanup(); //check to see if cleanupNeeds to be called.

        return result;
    }

    /**
     * call this everytime you think you might have done something that has
     * stopped the download (ie: everytime a download has been removed or the
     * download has been canceled.) The routines checks to see if the download
     * and all helper thread have stopped and if it has it calls the final
     * cleanup routine
     */
    private synchronized boolean endCheckAndCleanup() {
        if ((endFlag | isDone()) & (downloaders.size() == 0)) {
            cleanUp();

            return true;
        }

        return false;
    }

    public synchronized WorkSegment getNextWorkSegment(int requestedBlockSize) {
        if (unfinishedSegments.size() > 0)
            return ((WorkSegment)unfinishedSegments.pop()).recycled(true);
        
        final int multiBlockSize = Math.max(1, requestedBlockSize / DEFAULT_CHUNK_SIZE) * DEFAULT_CHUNK_SIZE; //quick round off to nearest chunk.
        
        long readLength = (fileLength - fileProgress > multiBlockSize ? multiBlockSize
                : fileLength - fileProgress);

        System.out.println("Main Thread -> Adding Work Segment " + fileProgress
                + " " + readLength);
        WorkSegment workSegment = new WorkSegment((readLength == 0 ? 0
                : fileProgress), readLength); // generate an end signal.

        fileProgress += readLength;

        return workSegment;
    }

    public synchronized void receiveExtraSegments(WorkSegment[] workSegments) {
        for (int i = 0; i < workSegments.length; i++) {
            unfinishedSegments.push(workSegments[i]);
        }
    }

    public synchronized void receiveDataBlock(DataBlock dataBlock) {
        try {
            randomAccessFile.seek(dataBlock.offset);

            randomAccessFile.write(dataBlock.bytes);

            bytesWrittenOut += dataBlock.bytes.length;

            dispatcher.fireEvent(new MultiSourceEvent(
                    MultiSourceEvent.PROGRESS, this));

            partialFile.setBit(dataBlock.offset / chunkSize);

            //if (isDone()) {
            //	flagToEnd(); //we're done.
            //}
        } catch (IOException ex) {
            ex.printStackTrace();
            flagToEnd();// humm.. maybe the user should be notified of this problem?
            //TODO add some sort of notification about these kinds of error here.
        }
    }

    public synchronized long getProgress() {
        return bytesWrittenOut;
    }

    public synchronized long getLength() {
        return fileLength;
    }

    public MysterFileStub getStub() {
        return stub;
    }

    public long getInitialOffset() {
        return initialOffset;
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

    //call when downlaod has completed sucessfully.
    private synchronized void done() {
        dispatcher.fireEvent(new MultiSourceEvent(
                MultiSourceEvent.DONE_DOWNLOAD, this));
    }

    //if is synchronized will cause deadlocks
    public void addListener(MSDownloadListener listener) {
        dispatcher.addListener(listener);
    }

    //if is synchronized will cause deadlocks
    public void removeListener(MSDownloadListener listener) {
        dispatcher.removeListener(listener);
    }

    public synchronized void flagToEnd() {
        if (endFlag | isDead)
            return; //shouldn't be called twice..

        endFlag = true;

        Enumeration enumeration = downloaders.elements();

        while (enumeration.hasMoreElements()) {
            InternalSegmentDownloader downloader = (InternalSegmentDownloader) enumeration
                    .nextElement();

            downloader.flagToEnd();
        }

        endCheckAndCleanup(); //just in case there was no downloader threads.
    }

    public void cancel() {
        if (isDead)
            return;

        isCancelled = true;
        flagToEnd();
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void end() {
        flagToEnd();

        while (!isDead) { //wow, is crap
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                return;
            }
        }

    }

    // Instead of putting a finally in the run() method, wen can put stuff in
    // here.
    // This method will only be called once right at the end of the download
    private synchronized void cleanUp() {
        MultiSourceHashSearch.removeHash(stub.getType(), hash, listener);

        try {
            randomAccessFile.close();
        } catch (Exception ex) {
        } // assert file is closed

        isDead = true;

        dispatcher.fireEvent(new MultiSourceEvent(
                MultiSourceEvent.END_DOWNLOAD, this));

        if (isDone()) {
            done();
        }
    }

    private class MSHashSearchListener extends HashSearchListener {
        public void startSearch(HashSearchEvent event) {
        }

        public void searchResult(HashSearchEvent event) {
            MysterFileStub stub = event.getFileStub();

            MultiSourceUtilities
                    .debug(stub == null ? "Search Lstnr-> No file with that hash here."
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

}

class InternalSegmentDownloader extends MysterThread implements
        SegmentDownloader {
    // Properties
    MysterFileStub stub;

    MysterSocket socket;

    Controller controller;

    //working variables
    WorkingSegment workingSegment;

    //Utility variables
    EventDispatcher dispatcher = new SyncEventDispatcher();

    //Utility working variables
    boolean endFlag = false;

    boolean deadFlag = false;

    boolean isActive = false; //is active says if the SegmentDownloader is
                              // actively downloading a file or if the download
                              // is queued

    //Params
    final int chunkSize;

    //Constants
    public static final int DEFAULT_MULTI_SOURCE_BLOCK_SIZE = 128 * 1024;
    
    //Static variables
    private static int instanceCounter = 0;

	private int idealBlockSize = DEFAULT_MULTI_SOURCE_BLOCK_SIZE;

    public InternalSegmentDownloader(Controller controller,
            MysterFileStub stub, int chunkSize) {
        super("SegmentDownloader " + (instanceCounter++) + " for "
                + stub.getName());

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
            socket = MysterSocketFactory.makeStreamConnection(stub
                    .getMysterAddress());
            //else socket = this.socket; //yuck.

            fireEvent(SegmentDownloaderEvent.CONNECTED, 0, 0, 0, 0);

            debug("Work Thread " + getName() + " -> Sending Section Type");
            socket.out
                    .writeInt(com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);

            debug("Work Thread " + getName() + " -> Checking Protocol");
            com.myster.client.stream.StandardSuite.checkProtocol(socket.in); //throws
                                                                             // Exception
                                                                             // if
                                                                             // bad

            debug("Work Thread " + getName() + " -> Doing Header");
            doHeader(socket);

            for (;;) {
                if (endFlag)
                    return;

                WorkSegment workSegment = controller.getNextWorkSegment(idealBlockSize); //(WorkSegment)workQueue.removeFromHead();

                if (workSegment == null)
                    return;

                workingSegment = new WorkingSegment(workSegment);

                if (!doWorkBlock(socket, workingSegment))
                    return; //this is for kill signals.. there are also
                            // exceptions

                workingSegment = null;
            }
        } catch (UnknownProtocolException ex) {
            com.myster.client.stream.StandardSuite
                    .disconnectWithoutException(socket);

            debug("Server doesn't understand multi-source download."); //some
                                                                       // sort
                                                                       // of
                                                                       // exception
                                                                       // should
                                                                       // be
                                                                       // thrown
                                                                       // here
                                                                       // so
                                                                       // that
                                                                       // callers
                                                                       // can
                                                                       // catch
                                                                       // this.
        } catch (IOException ex) {
            ex.printStackTrace(); //this code can handle exceptions so this is
                                  // really here to see if anything unexpected
                                  // has occured
        } finally {
            try {
                socket.close();
            } catch (Exception ex) {
            }

            finishUp();
        }
    }

    //Offset is offset within the file
    //lenght is the length of the current segment
    //progress is the progress through the segment (exclusing offset)
    private void fireEvent(int id, long offset, long progress,
            int queuePosition, long length) {
        fireEvent(id, offset,
                progress, queuePosition, length, "");
    }
    
    private void fireEvent(int id, long offset, long progress,
            int queuePosition, long length, String queuedMessage) {
        dispatcher.fireEvent(new SegmentDownloaderEvent(id, this, offset,
                progress, queuePosition, length, stub, queuedMessage));
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

    private boolean doWorkBlock(MysterSocket socket,
            WorkingSegment workingSegment) throws IOException {
        debug("Work Thread " + getName() + " -> Reading data "
                + workingSegment.workSegment.startOffset + " "
                + workingSegment.workSegment.length);

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
                int queuePosition = Integer
                        .parseInt(mml
                                .get(com.myster.server.stream.MultiSourceSender.QUEUED_PATH));

                String message = mml
                        .get(com.myster.server.stream.MultiSourceSender.MESSAGE_PATH);

                //if (message!=null) progress.setText(message); ///! Stuff for
                // event here

                debug("Queued pos ----> " + queuePosition + " " + message);

                if (queuePosition == 0) {
                    isActive = true; // now we're downloading
                    break; //yippy! on to download!
                }

                isActive = false; // blagh! Looks like we're queued!

                if (!controller.isOkToQueue())
                    throw new IOException("Should not be queued!");

                fireEvent(SegmentDownloaderEvent.QUEUED, 0, 0, queuePosition, 0, message);
            } catch (NumberFormatException ex) {
                throw new IOException(
                        "Server sent garble as queue position -> " + mml);
            }

            if (endFlag)
                throw new IOException("Was told to end.");
        }

        fireEvent(SegmentDownloaderEvent.START_SEGMENT,
                workingSegment.workSegment.startOffset, 0, 0,
                workingSegment.workSegment.length);//this isn't in the right
                                                   // place

        long timeTakenToDownloadSegment = 0;
        while (workingSegment.progress < workingSegment.workSegment.length) {
            debug("Work Thread " + getName() + " -> Reading in Type");

            if (socket.in.readInt() != 6669)
                throw new IOException("Client/Server lost sync");

            byte type = (byte) socket.in.read();

            switch (type) {
            case 'd':
                //progress.setText("Starting transfer...");
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
        fireEvent(SegmentDownloaderEvent.END_SEGMENT,
                workingSegment.workSegment.startOffset,
                workingSegment.workSegment.startOffset
                        + workingSegment.workSegment.length, 0,
                workingSegment.workSegment.length);

        return true;
    }

    private static int IDEAL_BLOCK_TIME = 60*1000;
	private int calculateNextBlockSize(long length, long timeTakenToDownloadSegment) {
		int maxLength = (int) (Math.min(length, Integer.MAX_VALUE / 2) * 2); //get the max packet length accounting for int overflow.
		return (int) Math.min(maxLength, (IDEAL_BLOCK_TIME * length) / timeTakenToDownloadSegment);
	}

	public boolean isActive() {
        return isActive;
    }

    private byte[] getDataBlock(MysterSocket socket) throws IOException {
        byte[] buffer = new byte[(int) socket.in.readLong()];

        socket.in.readFully(buffer);

        return buffer;
    }

    private void downloadDataBlock(MysterSocket socket,
            WorkingSegment workingSegment, long length) throws IOException {
        long bytesDownloaded = 0;

        byte[] buffer = new byte[chunkSize];
        
        for (bytesDownloaded = 0; bytesDownloaded < length;) {
            long calcBlockSize = (length - bytesDownloaded < chunkSize ? length
                    - bytesDownloaded : chunkSize);

            if (calcBlockSize != buffer.length) {
            	buffer = new byte[(int) calcBlockSize]; //could be made more
            }
            	
            if (endFlag)
                throw new IOException("was asked to end");

            socket.in.readFully(buffer);

            controller.receiveDataBlock(new DataBlock(workingSegment
                    .getCurrentOffset(), buffer));

            bytesDownloaded += calcBlockSize; //this line doesn't throw an IO
                                              // exception
            workingSegment.progress += calcBlockSize;

            fireEvent(SegmentDownloaderEvent.DOWNLOADED_BLOCK,
                    workingSegment.workSegment.startOffset, workingSegment
                            .getProgress(), 0,
                    workingSegment.workSegment.length); //nor this.
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
        System.out.println(string);
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

        public long setProgress(long progress) {
            return (this.progress = progress);
        } //using chaining to make chaining easy

        /**
         * Returns the remaining part of this work segment as a WorkSegment;
         * 
         * This call returns a stupid no-op work segment if it's done. Call
         * "isDone" to make sure the segment hasen't been finished before using
         * this method.
         */
        public WorkSegment getRemainingWorkSegment() {
            return new WorkSegment(workSegment.startOffset + progress,
                    workSegment.length - progress);
        }

        public boolean isDone() {
            return (progress == workSegment.length);
        }

        public long getCurrentOffset() {
            return (workSegment.startOffset + progress);
        }
    }
}

//Unsafe object due to lack of immutable arrays.

class DataBlock {
    public final byte[] bytes;

    public final long offset;

    public DataBlock(long offset, byte[] bytes) {
        this.offset = offset;
        this.bytes = bytes;
    }
}

//immutable!
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
    
    public WorkSegment recycled(boolean isRecycled){
        return new WorkSegment(startOffset, length, isRecycled);
    } 
}

interface Controller {
    public WorkSegment getNextWorkSegment(int requestedSize);

    public void receiveExtraSegments(WorkSegment[] workSegments);

    public void receiveDataBlock(DataBlock dataBlock);

    public boolean removeDownload(SegmentDownloader downloader);

    public boolean isOkToQueue(); //returns false if it's not ok to queue.
}