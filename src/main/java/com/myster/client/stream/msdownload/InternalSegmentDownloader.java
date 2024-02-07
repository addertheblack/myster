
package com.myster.client.stream.msdownload;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventThreadDispatcher;
import com.myster.client.stream.UnknownProtocolException;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.search.MysterFileStub;

class InternalSegmentDownloader implements SegmentDownloader {
    // Constants
    public static final int DEFAULT_MULTI_SOURCE_BLOCK_SIZE = 128 * 1024;

    // Static variables
    private static int instanceCounter = 0;

    // Utility variables
    private final EventDispatcher dispatcher = new SyncEventThreadDispatcher();

    // Params
    private final Controller controller;

    private final MysterFileStub stub;

    /**
     * This is the size of the tracked amount of data in an MSPartialFile.
     * 
     * We want to make sure we download data in units of this size.
     */
    private final int blockSize;

    // working variables
    private WorkingSegment workingSegment;

    private MysterSocket socket;

    /** 
     * This keeps the size of the next segment to download. Every time we download a segment we try and 
     * make this value equal 60 seconds worth of data.
     */
    private int idealSegmentSize = DEFAULT_MULTI_SOURCE_BLOCK_SIZE;

    // Utility working variables
    private boolean deadFlag = false;

    /**
     * is active says if the SegmentDownloader is actively downloading a file or
     * if the download is queued
     */
    private boolean isActive = false;

    private final String name;

    private final ExecutorService executor;

    private SocketFactory socketFactory;

    interface SocketFactory {
        MysterSocket makeStreamConnection(MysterAddress ip)
                throws IOException;
    }

    public InternalSegmentDownloader(Controller controller,
                                     SocketFactory socketFactory,
                                     MysterFileStub stub,
                                     int chunkSize) {
        this.socketFactory = socketFactory;
        this.name = "SegmentDownloader " + (instanceCounter++) + " for " + stub.getName();

        this.stub = stub;
        this.controller = controller;
        this.blockSize = chunkSize;

        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void addListener(SegmentDownloaderListener listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(SegmentDownloaderListener listener) {
        dispatcher.removeListener(listener);
    }

    public void start() {
        executor.execute(this::run);
    }
    
    public boolean isActive() {
        return isActive;
    }

    public boolean isDead() {
        return deadFlag;
    }
    
    public void flagToEnd() {
        executor.shutdownNow();

        try {
            socket.close();
        } catch (Exception ex) {
        }
    }    public int hashCode() {
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

    /** Package Protected for unit tests */
    void run() {
        try {
            socket = socketFactory.makeStreamConnection(stub.getMysterAddress());

            fireEvent(SegmentDownloaderEvent.CONNECTED, 0, 0, 0, 0);

            debug("Work Thread " + name + " -> Sending Section Type");
            socket.out.writeInt(com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);

            // throws Exception if bad
            debug("Work Thread " + name + " -> Checking Protocol");
            com.myster.client.stream.StandardSuite.checkProtocol(socket.in); 

            debug("Work Thread " + name + " -> Doing Header");
            doHeader(socket);

            for (;;) {
                if (executor.isShutdown()) {
                    return;
                }

                WorkSegment workSegment = controller.getNextWorkSegment(idealSegmentSize); // (WorkSegment)workQueue.removeFromHead();

                if (workSegment.isEndSignal()) {
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

        debug("Thread " + name + " -> Finished.");

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

    private boolean doWorkBlock(MysterSocket socket, WorkingSegment workingSegment)
            throws IOException {
        debug("Work Thread " + name + " -> Reading data "
                + workingSegment.workSegment.startOffset + " " + workingSegment.workSegment.length);

        socket.out.writeLong(workingSegment.workSegment.startOffset);
        socket.out.writeLong(workingSegment.workSegment.length);

        if (workingSegment.workSegment.isEndSignal()) {
            com.myster.client.stream.StandardSuite.disconnect(socket);
            return false;
        }

        waitInQueue(socket);

        fireEvent(SegmentDownloaderEvent.START_SEGMENT,
                  workingSegment.workSegment.startOffset,
                  0,
                  0,
                  workingSegment.workSegment.length);// this isn't in the
        // right
        // place

        long timeTakenToDownloadSegment = 0;
        while (workingSegment.getProgress() < workingSegment.workSegment.length) {
            debug("Work Thread " + name + " -> Reading in Type");

            if (socket.in.readInt() != 6669)
                throw new IOException("Client/Server lost sync");

            byte type = (byte) socket.in.read();

            switch (type) {
            case 'd':
                // progress.setText("Starting transfer...");
                long blockLength = socket.in.readLong();

                debug("Work Thread " + name + " -> Downloading start");
                final long startTime = System.currentTimeMillis();
                downloadDataBlock(socket, workingSegment, blockLength);
                timeTakenToDownloadSegment += System.currentTimeMillis() - startTime;
                debug("Work Thread " + name + " -> Downloading finished");

                break;
            default:
                fireEvent(type, getDataBlock(socket));
                break;
            }
        }
        if (!workingSegment.workSegment.recycled) {
            debug("Work Thread " + name + " -> Took " + (timeTakenToDownloadSegment / 1000)
                    + "s to download " + (workingSegment.workSegment.length / 1024) + "k");
            idealSegmentSize = calculateNextBlockSize(workingSegment.workSegment.length,
                    timeTakenToDownloadSegment);
            debug("Work Thread " + name + " -> next block will be " + (idealSegmentSize / 1024)
                    + "k");
        }
        fireEvent(SegmentDownloaderEvent.END_SEGMENT, workingSegment.workSegment.startOffset,
                workingSegment.workSegment.startOffset + workingSegment.workSegment.length, 0,
                workingSegment.workSegment.length);

        return true;
    }

    private void waitInQueue(MysterSocket socket) throws IOException {
        debug("Work Thread " + name + " -> Reading in QueuePostion");
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

            if (executor.isShutdown())
                throw new IOException("Was told to end.");
        }
    }

    private static int IDEAL_BLOCK_TIME_MS = 60 * 1000;

    private int calculateNextBlockSize(long length, long timeTakenToDownloadSegment) {
        // get the max packet length accounting for int overflow.
        int maxLength = (int) (Math.min(length, Integer.MAX_VALUE / 2) * 2);
        
        return (int) Math.min(maxLength, (IDEAL_BLOCK_TIME_MS * length) / Math.max(1, timeTakenToDownloadSegment));
    }

    private byte[] getDataBlock(MysterSocket socket) throws IOException {
        byte[] buffer = new byte[(int) socket.in.readLong()];

        socket.in.readFully(buffer);

        return buffer;
    }

    private void downloadDataBlock(MysterSocket socket, WorkingSegment workingSegment, long length)
            throws IOException {
        long bytesDownloaded = 0;

        byte[] buffer = new byte[blockSize];

        long lastProgressTime = System.currentTimeMillis();
        for (bytesDownloaded = 0; bytesDownloaded < length;) {
            long calcBlockSize = (length - bytesDownloaded < blockSize ? length - bytesDownloaded
                    : blockSize);

            if (calcBlockSize != buffer.length) {
                buffer = new byte[(int) calcBlockSize]; // could be made more
            }

            if (executor.isShutdown()) {
                throw new IOException("was asked to end");
            }

            socket.in.readFully(buffer);

            controller.receiveDataBlock(new DataBlock(workingSegment.getCurrentOffset(), buffer));

            bytesDownloaded += calcBlockSize;
            workingSegment.addProgress(calcBlockSize);

            if (System.currentTimeMillis() - lastProgressTime > 100) {
                fireEvent(SegmentDownloaderEvent.DOWNLOADED_BLOCK,
                        workingSegment.workSegment.startOffset, workingSegment.getProgress(), 0,
                        workingSegment.workSegment.length); // nor this.
                lastProgressTime = System.currentTimeMillis();
            }
        }
    }

    private static void debug(String string) {
        MultiSourceUtilities.debug(string);
    }

    /**
     * A working segment is a work segment with the amount progress through it.
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