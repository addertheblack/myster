package com.myster.net.stream.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.server.BannersManager;
import com.myster.net.server.ConnectionContext;
import com.myster.net.server.DownloadInfo;
import com.myster.net.server.ServerPreferences;
import com.myster.net.stream.client.MysterDataInputStream;
import com.myster.net.stream.client.MysterDataOutputStream;
import com.myster.net.stream.server.transferqueue.Downloader;
import com.myster.net.stream.server.transferqueue.MaxQueueLimitException;
import com.myster.net.stream.server.transferqueue.QueuedStats;
import com.myster.net.stream.server.transferqueue.TransferQueue;
import com.myster.server.event.ServerDownloadDispatcher;
import com.myster.server.event.ServerDownloadEvent;
import com.myster.server.event.ServerDownloadListener;
import com.myster.type.MysterType;

//1) read in offset(long) + length(long)

//send back queue position (int, negative if file not found) (not meta data) ->
//cont until 0
//read in continue <-

//send file + meta data in the same protocol as older one.

//When all of file has been sent ->

//repeat from 1

public class MultiSourceSender extends ServerStreamHandler {
    private static final Logger LOGGER = Logger.getLogger(MultiSourceSender.class.getName());
    
    public static final String QUEUED_PATH = "/queued";
    public static final String MESSAGE_PATH = "/message";

    public static final int SECTION_NUMBER = 90;

    private final ServerPreferences preferences;

    public MultiSourceSender(ServerPreferences preferences) {
        this.preferences = preferences;
    }

    public int getSectionNumber() {
        return SECTION_NUMBER;
    }

    public Object getSectionObject() {
        return new ServerDownloadDispatcher();
    }

    public void section(ConnectionContext context) throws IOException {
        MultiSourceDownloadInstance download =
                new MultiSourceDownloadInstance((ServerDownloadDispatcher) (context.sectionObject()),
                                                context.transferQueue(),
                                                new MysterAddress(context.socket().getInetAddress()));

        try {
            download.download(context.socket(), context.fileManager());
        } finally {
            download.endBlock();
        }
    }

    private static class UploadBlock {
        public final long start;

        public final long size;

        public UploadBlock(long param_start, long param_size) {
            start = param_start;
            size = param_size;
        }

        public boolean isEndSignal() {
            return (start == 0) && (size == 0);
        }
    }

    private class MultiSourceDownloadInstance {
        private static long CHUNK_SIZE = 8000;
        
        private volatile boolean endFlag = false;

        private final ServerDownloadDispatcher dispatcher;
        private final MysterAddress remoteIP;
        private final DownloadInfo downloadInfo;
        private final TransferQueue transferQueue;

        private volatile MysterSocket socket;

        private String fileName = "??";

        private MysterType type = null;

        private long fileLength = 0;
        private long startTime = System.currentTimeMillis();
        private long amountDownloaded = 0;
        private long myCounter = 0;
        private long offset = 0;

        public MultiSourceDownloadInstance(ServerDownloadDispatcher dispatcher,
                                           TransferQueue transferQueue,
                                           MysterAddress remoteIP) {
            this.dispatcher = dispatcher;
            this.downloadInfo = new Stats();
            this.transferQueue = transferQueue;
            this.remoteIP = remoteIP;

            fire().downloadSectionStarted(newEvent(-1));
        }

        public void download(final MysterSocket socket, FileTypeListManager fileManager) throws IOException {
            try {
                this.socket = socket;//this is so I can disconnect the stupid
                // socket.

                final MysterDataOutputStream out = socket.out;
                final MysterDataInputStream in = socket.in;
                
                type = in.readType();
                fileName = in.readUTF();

                final File file = fileManager.getFile(type, fileName);

                if (file == null) {
                    out.write(0);
                    return;
                } else {
                    out.write(1);
                }

                checkForLeechers(socket); //throws an IO Exception if there's a
                // leech.

                final UploadBlock currentBlock = startNewBlock(socket, file);

                fileLength = file.length();

                if (currentBlock.isEndSignal()) { //must fix this duplicate
                    // code!!!!!! AGHHH!!
                    this.offset = this.fileLength;
                    amountDownloaded = 0;
                    LOGGER.info("GOT END SIGNAL: " + this.offset + " : " + this.fileLength);
                    return;
                }

                try { //the first loop is special because it can be queued.
                    // Other loops do not get queued...
                    transferQueue.doDownload(new Downloader() {
                        public void download() throws IOException {
                            sendQueuePosition(socket.out, 0, "You are ready to download..");

                            fire().downloadStarted(newEvent(-1));

                            sendImage(socket.out); //sends an "ad"
                            // image and URL

                            startTime = System.currentTimeMillis();
                            sendFileSection(socket, file, currentBlock); //send
                            // the
                            // first
                            // block

                            fire().downloadFinished(newEvent(-1));

                            blockSendingLoop(socket, file);
                        }

                        public void queued(QueuedStats stats) throws IOException {
                            sendQueuePosition(socket.out, stats.queuePosition(),
                                    "You are in a queue to download..");
                        }

                        public MysterAddress getAddress() {
                            return remoteIP;
                        }
                    });
                } catch (MaxQueueLimitException ex) {
                    sendQueuePosition(socket.out, transferQueue.getMaxQueueLength()+1, "Too busy to accept downloads right now..");
                    throw new IOException("Over the queue limit, disconnecting..", ex);
                }
            } catch (DoneIoException ex) {
                // this means the client sent us a signal that he's done and about to close the connection
                throw ex;
            } catch (IOException ex) {
                ex.printStackTrace();
                throw ex;
            } finally {
                fire().downloadFinished(newEvent(-1));
            }
        }

        private UploadBlock startNewBlock(MysterSocket socket, File file) throws IOException {
            startTime = System.currentTimeMillis();

            UploadBlock uploadBlock = getNextBlockToSend(socket, file);

            return uploadBlock;
        }

        //NOTE: The first loop is not done here.
        private void blockSendingLoop(MysterSocket socket, File file) throws IOException {
            for (;;) {
                UploadBlock currentBlock = startNewBlock(socket, file);

                if (currentBlock.isEndSignal()) {
                    this.offset = this.fileLength;
                    amountDownloaded = 0;
                    LOGGER.info("GOT END SIGNAL: " + this.offset + " : " + this.fileLength);
                    break;
                }

                amountDownloaded = 0; //reset amount to 0.

                sendQueuePosition(socket.out, 0, "Download is starting now..");

                fire().downloadStarted(newEvent(-1));

                sendImage(socket.out);

                sendFileSection(socket, file, currentBlock);

                fire().downloadFinished(newEvent(-1));
            }
        }

        //Encapsulates the stuff required to send a queue position
        private void sendQueuePosition(MysterDataOutputStream out, int queued, String message)
                throws IOException {
            RobustMML mml = new RobustMML();

            mml.put(QUEUED_PATH, "" + queued); //no queing
            if (!message.equals(""))
                mml.put(MESSAGE_PATH, message);

            out.writeUTF("" + mml); //this would loop until 1
            
            out.flush();

            fire().queued(newEvent(queued));
        }

        //Throws an IOException if there's a leech.
        private void checkForLeechers(MysterSocket socket) throws IOException {
            if (preferences.isKickFreeloaders()) {
                try {
                    MysterSocket s = com.myster.net.stream.client.MysterSocketFactory
                            .makeStreamConnection(new com.myster.net.MysterAddress(socket
                                    .getInetAddress()));

                    try {
                        s.close();
                    } catch (Exception _) {
                        // nothing
                    }
                } catch (IOException _) { 
                    // if host is not reachable it will
                    // end up here.
                    sendQueuePosition(socket.out, 0, "You are not reachable from the outside");

                    // sends and image complaining about firewalls
                    freeloaderComplain(socket.out); 

                    throw new IOException("Downloader is a leech");
                }
            }
        }
        
        //code 'i'
        public static void sendImage(MysterDataOutputStream out) throws IOException {
            MysterDataInputStream in = null;
            File file;

            String imageName = BannersManager.getNextImageName();

            if (imageName == null)
                return;

            file = BannersManager.getFileFromImageName(imageName);

            if (file == null) { //is needed (Threading issue)
                return;
            }

            try {
                in = new MysterDataInputStream(new FileInputStream(file));

                byte[] bytearray = new byte[(int) file.length()];

                in.readFully(bytearray, 0, (int) file.length());

                out.writeInt(6669);
                out.write('i');
                out.writeLong(bytearray.length);
                out.write(bytearray);

                sendURLFromImageName(out, imageName);
            } finally {
                try {
                    in.close();
                } catch (Exception _) {
                    // nothing
                }
            }

        }
        
        //A utility method that allows one to send a banner URL from an Image
        // name
        public static void sendURLFromImageName(MysterDataOutputStream out,
                String imageName) throws IOException {
            String url = BannersManager.getURLFromImageName(imageName);

            if (url == null)
                return;

            sendURL(out, url);
        }

        //code 'u'
        public static void sendURL(MysterDataOutputStream out, String url)
                throws IOException {
            out.writeInt(6669);
            out.write('u');
            out.writeInt(0); //padding 'cause writeUTF preceed the UTF with a
                             // short.
            out.writeShort(0);
            out.writeUTF(url);
        }
        
        public static void freeloaderComplain(MysterDataOutputStream out)
                throws IOException {
            byte[] queuedImage = new byte[4096];
            int sizeOfImage = 0;
            int tempint;

            try (InputStream qin = MultiSourceSender.class.getResourceAsStream("firewall.gif")) {

                // loading image...

                do {
                    tempint = qin.read(queuedImage, sizeOfImage, 4096 - sizeOfImage);
                    if (tempint > 0)
                        sizeOfImage += tempint;
                } while (tempint != -1);
            }
            
            // mapping errors.
            if (sizeOfImage == -1)
                sizeOfImage = -1;
            if (sizeOfImage == 4096)
                sizeOfImage = -1;

            out.writeInt(6669);
            out.writeByte('i');
            out.writeLong(sizeOfImage);
            out.write(queuedImage, 0, sizeOfImage);

            sendURL(out, "http://www.mysternetworks.com/information/dl_error_faq.html");
        }

        private void endBlock() {
            fire().downloadSectionFinished(newEvent(-1));
        }

        private void sendFileSection(final MysterSocket socket, final File file_arg,
                final UploadBlock currentBlock) throws IOException {
            long offset = currentBlock.start, length = currentBlock.size;
            RandomAccessFile file = null;

            try {
                file = new RandomAccessFile(file_arg, "r");
                file.seek(offset);

                byte[] buffer = new byte[(int) CHUNK_SIZE];

                socket.out.writeInt(6669);
                socket.out.write('d');
                socket.out.writeLong(length);

                for (long counter = 0; counter < (length);) {
                    long calcBlockSize =
                            (length - counter < CHUNK_SIZE ? length - counter : CHUNK_SIZE);

                    if (endFlag)
                        throw new DisconnectCommandException();

                    file.readFully(buffer, 0, (int) calcBlockSize);

                    socket.out.write(buffer, 0, (int) calcBlockSize);

                    counter += calcBlockSize;
                    amountDownloaded = counter; // for stats
                }

                myCounter += length; //this is so a client cannot suck data
                // forever.
            } finally {
                if (file != null)
                    file.close();
            }
        }
        
        class DoneIoException extends IOException {
            // nothing here
        }

        // TODO: Make it so we can shutdown cleanly
        // final long offset = socket.in.readLong(); just fails with a nasty looking exception when client
        // diconnects.
        private UploadBlock getNextBlockToSend(MysterSocket socket, File file) throws IOException {
            final long offset = socket.in.readLong();
            long fileLength = socket.in.readLong();
            
            if (offset == fileLength) {
                throw new DoneIoException();
            }

            if ((fileLength < 0) | (offset < 0) | ((fileLength == 0) & (offset != 0))
                    | (fileLength + offset > file.length())) {
                throw new IOException("Client sent garbage fileLengths and offsets of "
                        + fileLength + " and " + offset);
            }

            if (myCounter > file.length()) {
                throw new IOException("User has request more bytes than there are in the file!");
            }

            this.offset = offset;

            return new UploadBlock(offset, fileLength);

        }

        private ServerDownloadListener fire() {
            return dispatcher.fire();
        }

        private ServerDownloadEvent newEvent(int queuePosition) {
            return new ServerDownloadEvent(remoteIP,
                                           getSectionNumber(),
                                           fileName,
                                           ServerDownloadEvent.NO_BLOCK_TYPE,
                                           offset + amountDownloaded,
                                           fileLength,
                                           downloadInfo,
                                           queuePosition);
        }

        private class Stats implements DownloadInfo {
            public double getTransferRate() {
                long elapsedTime = System.currentTimeMillis() - startTime;

                final int ONE_SECOND = 1000;

                if ((elapsedTime < ONE_SECOND) || (amountDownloaded == 0))
                    return 0;

                return (double) amountDownloaded / (double) (elapsedTime / ONE_SECOND);
            }

            public long getStartTime() {
                return startTime;
            }

            public long getAmountDownloaded() {
                return offset + amountDownloaded;
            }

            public long getInititalOffset() {
                return offset;
            }

            public String getFileName() {
                return fileName;
            }

            public String getFileType() {
                return type.toString();
            }

            public long getFileSize() {
                return fileLength;
            }

            public void disconnectClient() {
                endFlag = true;

                if (socket == null)
                    return;

                try {
                    socket.close();
                } catch (Exception _) {
                    // nothing
                }
            }
        }
    }

    private static class DisconnectCommandException extends IOException {
        // nothing
    }
}